#!/usr/bin/env python3
"""Migration des libellés de l'interface Android : tables Kotlin -> res/values*/strings.xml.

Lit les sources de la révision BASE (par défaut origin/main, avant migration) avec `git show`, puis :
  a) extrait chaque `t(en, fr, es, ar)`, chaque `when (lang) { ... }` simple et les tables nommées de Strings.kt
     et de MobileStrings.kt ;
  b) écrit res/values/strings.xml (anglais, source), values-fr, values-es, values-ar ;
  c) réécrit les définitions Kotlin : `t("…", "…")` devient `tx(R.string.cle)` (les noms de propriétés et les
     signatures ne changent pas) ; les `when (lang)` à pluriel deviennent `tp(R.plurals.cle, n, …)`.

Ce qui n'est pas reconnu est laissé tel quel et listé dans le rapport (à reprendre à la main : voir manual.py).

Usage : python tools/i18n/migrate.py [--base origin/main] [--report tools/i18n/report.json]
Les clés absentes de values-es / values-ar (valeur par défaut = anglais dans l'ancien code) ne sont pas écrites :
le repli natif d'Android affiche l'anglais ; elles sont listées dans le rapport.
"""
import argparse
import json
import re
import subprocess
import sys
from collections import OrderedDict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from ktscan import code_mask, lex_string, skip_balanced, snake, split_args  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
KT = "android-native/app/src/main/kotlin/com/ultratv/tv/nativeapp/"
RES = ROOT / "android-native/app/src/main/res"
LANGS = ["en", "fr", "es", "ar"]
LANG_OF = {"English": "en", "French": "fr", "Spanish": "es", "Arabic": "ar"}

# fichier -> préfixe de clés
FILES = {
    "i18n/DesignStrings.kt": "ds", "i18n/DesignStringsB1.kt": "dsb1", "i18n/DirectGuideStrings.kt": "dg",
    "i18n/GuideStrings.kt": "guide", "i18n/HomeV2Strings.kt": "home2", "i18n/PersonStrings.kt": "person",
    "i18n/PersonalizationStrings.kt": "perso", "i18n/PhoneSendStrings.kt": "phone", "i18n/RemoteStrings.kt": "remote",
    "i18n/SimpleStrings.kt": "simple", "i18n/TraktStrings.kt": "trakt", "i18n/CatalogCategoryStrings.kt": "cat",
    "ui/player/PlayerExtraStrings.kt": "pex", "ui/memory/MemoryStrings.kt": "mem", "ui/downloads/DownloadStrings.kt": "dl",
    "ui/profile/ProfileStrings.kt": "prof", "ui/settings/CloudSyncStrings.kt": "cloud", "ui/settings/DnsStrings.kt": "dns",
    "ui/account/AccountScreen.kt": "acct", "ui/live/LivePreview.kt": "lp",
}


def git_show(base, rel):
    return subprocess.run(["git", "show", f"{base}:{rel}"], cwd=ROOT, capture_output=True, check=True).stdout.decode("utf-8")


class Catalog:
    """Toutes les entrées : clé -> {'kind', 'values': {lang: texte | {quantité: texte}}, 'src': fichier}."""

    def __init__(self):
        self.entries = OrderedDict()
        self.sections = []  # (fichier, [clés])
        self.defaulted = {"es": [], "ar": []}
        self.manual = []

    def unique(self, key):
        k, n = key, 2
        while k in self.entries:
            k = f"{key}_{n}"
            n += 1
        return k


CAT = Catalog()


# ---------------------------------------------------------------- gabarits

def build_template(parts_by_lang, exprs_order=None):
    """parts_by_lang : {lang: parts}. Renvoie ({lang: texte %1$s}, [expressions]) ou None si non convertible."""
    exprs = []
    for lang in LANGS:
        for kind, v in parts_by_lang.get(lang, []):
            if kind == "expr":
                if '"' in v:
                    return None
                if v not in exprs:
                    exprs.append(v)
    out = {}
    for lang, parts in parts_by_lang.items():
        txt = []
        for kind, v in parts:
            if kind == "text":
                txt.append(v.replace("%", "%%") if exprs else v)
            else:
                txt.append(f"%{exprs.index(v) + 1}$s")
        out[lang] = "".join(txt)
    return out, exprs


# ---------------------------------------------------------------- analyse d'un fichier

DECL = re.compile(
    r"\b(?:val|var)\s+(?:[\w.<>?]+\.)?(\w+)(?:\s*:\s*[\w.<>?]+)?\s+get\(\)|\bfun\s+(?:<[^>]*>\s*)?(?:[\w.<>?]+\.)?(\w+)\s*\(")
LABEL = re.compile(r"(?:([A-Za-z_][\w.]*)|\"([^\"]*)\")\s*->\s*$")
TDEF = re.compile(r"^[ \t]*private fun (?:(\w+)\.)?t\(", re.M)
WHEN = re.compile(r"\bwhen\s*\(\s*lang\s*\)\s*\{")
HEAD = re.compile(r"(?:AppLang\.\w+|else)(?:\s*,\s*AppLang\.\w+)*\s*->")
ARGNAME = re.compile(r"^(en|fr|es|ar)\s*=\s*", re.S)


def line_of(src, pos):
    return src.count("\n", 0, pos) + 1


def decl_name(src, mask, p):
    last = None
    for m in DECL.finditer(src, 0, p):
        if not mask[m.start()]:
            continue
        ls = src.rfind("\n", 0, m.start()) + 1
        prefix = src[ls:m.start()]
        if prefix.strip() == "" and len(prefix) > 4:
            continue  # fonction ou variable locale
        last = m
    return (last.group(1) or last.group(2)) if last else "x"


def label_before(src, p):
    m = LABEL.search(src[max(0, p - 120):p])
    if not m:
        return None
    raw = m.group(1) or m.group(2)
    return snake(raw.split(".")[-1]) or None


def process_file(rel, prefix, base):
    src = git_show(base, KT + rel)
    mask = code_mask(src)
    edits = []  # (start, end, texte)
    removed = []
    is_ext = False
    keep_helpers = False

    # 1. définition(s) de t(...)
    for m in TDEF.finditer(src):
        if not mask[m.start() + (len(m.group(0)) - len(m.group(0).lstrip()))]:
            continue
        w = WHEN.search(src, m.end())
        end = skip_balanced(src, w.end() - 1, "{", "}")
        end2 = end + 1 if src[end:end + 1] == "\n" else end
        removed.append((m.start(), end2))
        if m.group(1):
            is_ext = True
            edits.append((m.start(), end2, ""))
        elif rel == "i18n/DesignStrings.kt":
            edits.append((m.start(), end2, ""))
        else:
            keep_helpers = True
            edits.append((m.start(), end2,
                          "    private fun tx(id: Int, vararg args: Any): String = I18n.string(lang, id, *args)\n"
                          "    private fun tp(id: Int, qty: Int, vararg args: Any): String = I18n.plural(lang, id, qty, *args)\n"))

    def in_removed(pos):
        return any(a <= pos < b for a, b in removed)

    sites = []  # dicts : start, end, decl, label, kind, values, args, qty

    # 2. appels t(...)
    for m in re.finditer(r"(?<![\w])t\(", src):
        p = m.start()
        if not mask[p] or in_removed(p) or src[max(0, p - 4):p] == "fun ":
            continue
        close = skip_balanced(src, p + 1, "(", ")")
        arglist = split_args(src, p + 2, close - 1)
        parts, ok = {}, True
        for idx, (a, b) in enumerate(arglist):
            text = src[a:b].strip()
            lang = LANGS[idx] if idx < 4 else None
            nm = ARGNAME.match(text)
            if nm:
                lang, text = nm.group(1), text[nm.end():]
            if lang is None or not text.startswith('"'):
                ok = False
                break
            ps, e = lex_string(text, 0)
            if e != len(text):
                ok = False
                break
            parts[lang] = ps
        if not ok or "en" not in parts or "fr" not in parts:
            CAT.manual.append(f"{rel}:{line_of(src, p)}: t(...) non littéral")
            continue
        bt = build_template(parts)
        if bt is None:
            CAT.manual.append(f"{rel}:{line_of(src, p)}: t(...) avec expression à littéral")
            continue
        vals, exprs = bt
        for lg in ("es", "ar"):
            if lg not in vals:
                CAT.defaulted[lg].append(None)  # remplacé plus bas par la clé
        sites.append(dict(start=p, end=close, decl=decl_name(src, mask, p), label=label_before(src, p), kind="string",
                          values=vals, args=exprs, qty=None, line=line_of(src, p)))

    # 3. when (lang) { ... }
    for m in WHEN.finditer(src):
        p = m.start()
        if not mask[p] or in_removed(p):
            continue
        open_i = m.end() - 1
        close = skip_balanced(src, open_i, "{", "}")
        site = parse_when(src, mask, rel, open_i, close)
        if site is None:
            continue
        site.update(start=p, end=close, decl=decl_name(src, mask, p), label=None, line=line_of(src, p))
        sites.append(site)

    # 4. clés
    sites.sort(key=lambda s: s["start"])
    groups = OrderedDict()
    for s in sites:
        groups.setdefault((s["decl"], s["start"] // 1 if False else None), []).append(s)
    by_decl = OrderedDict()
    for s in sites:
        by_decl.setdefault(s["decl"], []).append(s)
    keys_here = []
    for decl, lst in by_decl.items():
        base_key = f"{prefix}_{snake(decl)}"
        for i, s in enumerate(lst):
            if len(lst) == 1:
                k = base_key
            else:
                k = f"{base_key}_{s['label'] or (i + 1)}"
            k = CAT.unique(k)
            s["key"] = k
    for s in sites:
        k = s["key"]
        CAT.entries[k] = dict(kind=s["kind"], values=s["values"], src=rel)
        keys_here.append(k)
        for lg in ("es", "ar"):
            if lg not in s["values"]:
                CAT.defaulted[lg].append(k)
        args = "".join(", " + a for a in s["args"])
        if s["kind"] == "string":
            edits.append((s["start"], s["end"], f"tx(R.string.{k}{args})"))
        else:
            edits.append((s["start"], s["end"], f"tp(R.plurals.{k}, {s['qty']}{args})"))
    CAT.defaulted["es"] = [k for k in CAT.defaulted["es"] if k]
    CAT.defaulted["ar"] = [k for k in CAT.defaulted["ar"] if k]
    CAT.sections.append((rel, keys_here))

    out = apply_edits(src, edits)
    out = add_imports(out, rel)
    (ROOT / KT / rel).write_text(out, encoding="utf-8", newline="\n")


def parse_when(src, mask, rel, open_i, close):
    """Analyse un when (lang) : renvoie un site {kind, values, args, qty} ou None (reporté en manuel)."""
    inner0, inner1 = open_i + 1, close - 1
    heads, depth, j = [], 0, inner0
    while j < inner1:
        if mask[j]:
            c = src[j]
            if c in "({[":
                depth += 1
            elif c in ")}]":
                depth -= 1
            elif depth == 0 and (j == inner0 or not (src[j - 1].isalnum() or src[j - 1] == "_")):
                hm = HEAD.match(src, j)
                if hm:
                    heads.append((j, hm.end(), hm.group(0)))
                    j = hm.end()
                    continue
        j += 1
    if not heads:
        CAT.manual.append(f"{rel}:{line_of(src, open_i)}: when(lang) sans branches reconnues")
        return None
    parts_lang, plural_lang, qty, ok = {}, {}, None, True
    for n, (hs, he, htxt) in enumerate(heads):
        be = heads[n + 1][0] if n + 1 < len(heads) else inner1
        body = src[he:be].strip().rstrip(";").strip()
        langs = [LANG_OF[x] for x in re.findall(r"AppLang\.(\w+)", htxt)] or ["en"]
        if htxt.startswith("else"):
            langs = ["en"]
        if body.startswith('"'):
            ps, e = lex_string(body, 0)
            if e != len(body):
                ok = False
                break
            for lg in langs:
                parts_lang[lg] = ps
            continue
        im = re.match(r"if\s*\(\s*(\w+)\s*(==|<=)\s*1\s*\)\s*", body)
        if im and body[im.end():].startswith('"'):
            a_parts, e1 = lex_string(body, im.end())
            rest = body[e1:].lstrip()
            if not rest.startswith("else"):
                ok = False
                break
            rest = rest[4:].lstrip()
            if not rest.startswith('"'):
                ok = False
                break
            b_parts, e2 = lex_string(rest, 0)
            if e2 != len(rest):
                ok = False
                break
            if qty not in (None, im.group(1)):
                ok = False
                break
            qty = im.group(1)
            for lg in langs:
                plural_lang[lg] = (a_parts, b_parts)
            continue
        ok = False
        break
    if not ok:
        CAT.manual.append(f"{rel}:{line_of(src, open_i)}: when(lang) à reprendre à la main")
        return None
    if "en" not in parts_lang and "en" not in plural_lang:
        CAT.manual.append(f"{rel}:{line_of(src, open_i)}: when(lang) sans else")
        return None
    if not plural_lang:
        bt = build_template(parts_lang)
        if bt is None:
            CAT.manual.append(f"{rel}:{line_of(src, open_i)}: when(lang) avec expression à littéral")
            return None
        vals, exprs = bt
        return dict(kind="string", values=vals, args=exprs, qty=None)
    # pluriel
    allparts = {}
    for lg, ps in parts_lang.items():
        allparts[lg] = ps
    # on construit un gabarit commun sur toutes les parties de tous les textes
    flat = {}
    for lg, ps in parts_lang.items():
        flat[f"{lg}|other"] = ps
    for lg, (a, b) in plural_lang.items():
        flat[f"{lg}|one"] = a
        flat[f"{lg}|other"] = b
    exprs = []
    for k in sorted(flat, key=lambda x: (LANGS.index(x.split("|")[0]), x)):
        for kind, v in flat[k]:
            if kind == "expr":
                if '"' in v:
                    CAT.manual.append(f"{rel}:{line_of(src, open_i)}: pluriel avec expression à littéral")
                    return None
                if v not in exprs:
                    exprs.append(v)

    def render(ps):
        return "".join(v.replace("%", "%%") if k == "text" and exprs else (v if k == "text" else f"%{exprs.index(v) + 1}$s") for k, v in ps)

    values = {}
    for lg, ps in parts_lang.items():
        values[lg] = {"other": render(ps)}
    for lg, (a, b) in plural_lang.items():
        a_txt, b_txt = render(a), render(b)
        # « 1 canal » littéral : le « 1 » devient le nombre (en français, 0 est aussi au singulier : « 0 saison »)
        a_plain = "".join(v for k, v in a if k == "text") if all(k == "text" for k, _ in a) else None
        b_exprs = [v for k, v in b if k == "expr"]
        if a_plain is not None and len(set(b_exprs)) == 1 and a_plain.count("1") == 1:
            a_txt = a_plain.replace("%", "%%").replace("1", f"%{exprs.index(b_exprs[0]) + 1}$s")
        values[lg] = {"one": a_txt, "other": b_txt}
    return dict(kind="plural", values=values, args=exprs, qty=qty)


def apply_edits(src, edits):
    edits = sorted(edits, key=lambda e: e[0], reverse=True)
    last = len(src) + 1
    for s, e, t in edits:
        assert e <= last, "édition chevauchante"
        src = src[:s] + t + src[e:]
        last = s
    return src


def add_imports(src, rel):
    need = ["com.ultratv.tv.nativeapp.R"]
    if not rel.startswith("i18n/"):
        need.append("com.ultratv.tv.nativeapp.i18n.I18n")
    for imp in need:
        if f"import {imp}\n" in src:
            continue
        m = list(re.finditer(r"^import .*\n", src, re.M))
        if m:
            at = m[-1].end()
            src = src[:at] + f"import {imp}\n" + src[at:]
        else:
            pm = re.search(r"^package .*\n", src, re.M)
            src = src[:pm.end()] + f"\nimport {imp}\n" + src[pm.end():]
    return src


# ---------------------------------------------------------------- tables nommées (Strings.kt, MobileStrings.kt)

def parse_ctor(src, open_i, group=""):
    """Lit `Nom(champ = "…", sous = Autre(...))` à partir de la parenthèse ouvrante. Renvoie [(groupe, champ, parts)]."""
    close = skip_balanced(src, open_i, "(", ")")
    out, j = [], open_i + 1
    end = close - 1
    while j < end:
        m = re.compile(r"\s*,?\s*(?://[^\n]*\n\s*)*(\w+)\s*=\s*").match(src, j)
        if not m:
            if src[j:end].strip(" \n,\t"):
                if re.match(r"\s*,?\s*(?://[^\n]*)?\s*$", src[j:end]) is None:
                    raise ValueError("champ inattendu : " + src[j:j + 60])
            break
        name = m.group(1)
        j = m.end()
        if src[j] == '"':
            parts, j = lex_string(src, j)
            out.append((group, name, parts))
        else:
            nm = re.compile(r"\w+\(").match(src, j)
            if not nm:
                raise ValueError("valeur inattendue pour " + name + " : " + src[j:j + 60])
            sub = parse_ctor(src, nm.end() - 1, name)
            out.extend(sub)
            j = skip_balanced(src, nm.end() - 1, "(", ")")
    return out


def named_tables(rel, prefix_of_group, base, vals_names):
    """Renvoie (src, {lang: [(groupe, champ, parts)]}, {lang: (début, fin)})."""
    src = git_show(base, KT + rel)
    tabs, spans = {}, {}
    for lg, nm in vals_names.items():
        m = re.search(r"(?:private )?val " + nm + r" = (\w+)\(", src)
        open_i = m.end() - 1
        tabs[lg] = parse_ctor(src, open_i)
        spans[lg] = (m.start(), skip_balanced(src, open_i, "(", ")"))
    return src, tabs, spans


def register_named(rel, tabs, prefix_of_group):
    """Ajoute au catalogue ; renvoie la liste ordonnée [(groupe, champ, clé)]."""
    en = tabs["en"]
    order, keys_here = [], []
    index = {lg: {(g, f): p for g, f, p in tabs[lg]} for lg in LANGS}
    for g, f, parts in en:
        vals = {}
        for lg in LANGS:
            if (g, f) in index[lg]:
                ps = index[lg][(g, f)]
                if any(k == "expr" for k, _ in ps):
                    raise ValueError(f"gabarit Kotlin dans une table nommée : {g}.{f}")
                vals[lg] = "".join(v for _, v in ps)
        key = CAT.unique(prefix_of_group(g) + snake(f))
        CAT.entries[key] = dict(kind="string", values=vals, src=rel)
        keys_here.append(key)
        order.append((g, f, key))
    CAT.sections.append((rel, keys_here))
    return order


def gen_ctor(order, cls_of_group, indent="        "):
    """Texte Kotlin des arguments nommés (groupes imbriqués)."""
    lines, cur = [], None
    top = [(g, f, k) for g, f, k in order if g == ""]
    groups = OrderedDict()
    for g, f, k in order:
        if g:
            groups.setdefault(g, []).append((f, k))
    out = []
    for g, f, k in top:
        out.append(f"{indent}{f} = tx(R.string.{k}),")
    for g, items in groups.items():
        out.append(f"{indent}{g} = {cls_of_group[g]}(")
        for f, k in items:
            out.append(f"{indent}    {f} = tx(R.string.{k}),")
        out.append(f"{indent}),")
    return "\n".join(out)


def process_strings_kt(base):
    rel = "i18n/Strings.kt"
    src, tabs, spans = named_tables(rel, None, base, {"en": "EN", "fr": "FR", "es": "ES", "ar": "AR"})
    pref = {"": "str_", "wiz": "wiz_", "sync": "syncerr_", "common": "common_"}
    order = register_named(rel, tabs, lambda g: pref[g])
    ctor = gen_ctor(order, {"wiz": "WizardStrings", "sync": "SyncErrorStrings", "common": "CommonStrings"})
    first = spans["en"][0]
    last = spans["ar"][1]
    head = src[:first]
    tail_m = re.search(r"\n@Composable\nfun stringsFor", src)
    tail = src[tail_m.start():]
    build = (
        "/** Construit les textes de l'interface dans la langue demandée, depuis res/values (+ values-xx)/strings.xml. */\n"
        "private fun buildStrings(lang: AppLang): Strings {\n"
        "    fun tx(id: Int): String = I18n.string(lang, id)\n"
        "    return Strings(\n" + ctor + "\n    )\n}\n\n"
        "/** Textes de l'interface pour une langue (cache par langue résolue). Utilisable hors Compose. */\n"
        "fun stringsOf(lang: AppLang): Strings {\n"
        "    val resolved = I18n.resolve(lang)\n"
        "    return I18n.cached(\"Strings\", resolved) { buildStrings(resolved) }\n"
        "}\n"
    )
    new_tail = tail
    new_tail = re.sub(r"@Composable\nfun stringsFor\(lang: AppLang\): Strings \{.*?\n\}\n",
                      "@Composable\nfun stringsFor(lang: AppLang): Strings {\n"
                      "    // Lecture de la configuration : recompose quand la langue de l'appareil change (mode Automatique).\n"
                      "    LocalConfiguration.current\n"
                      "    return stringsOf(lang)\n}\n", new_tail, flags=re.S)
    new_tail = new_tail.replace("compositionLocalOf<Strings> { EN }", "compositionLocalOf<Strings> { stringsOf(AppLang.English) }")
    head = head.replace(
        "package com.ultratv.tv.nativeapp.i18n\n",
        "package com.ultratv.tv.nativeapp.i18n\n\nimport com.ultratv.tv.nativeapp.R\n", 1)
    out = head + build + new_tail
    (ROOT / KT / rel).write_text(out, encoding="utf-8", newline="\n")


def process_mobile_kt(base):
    rel = "ui/mobile/MobileStrings.kt"
    src, tabs, spans = named_tables(rel, None, base, {"en": "EN", "fr": "FR", "es": "ES", "ar": "AR"})
    order = register_named(rel, tabs, lambda g: "mob_")
    ctor = gen_ctor(order, {}, indent="            ")
    first = min(s[0] for s in spans.values())
    last = max(s[1] for s in spans.values())
    # début de la ligne du premier val ; fin = fin du dernier
    first = src.rfind("\n", 0, first) + 1
    new = (
        "        private fun build(lang: AppLang): MobileStrings {\n"
        "            fun tx(id: Int): String = I18n.string(lang, id)\n"
        "            return MobileStrings(\n" + ctor + "\n            )\n        }\n\n"
        "        val EN get() = forLang(AppLang.English, \"en\")\n"
        "        val FR get() = forLang(AppLang.French, \"fr\")\n"
        "        val ES get() = forLang(AppLang.Spanish, \"es\")\n"
        "        val AR get() = forLang(AppLang.Arabic, \"ar\")\n"
    )
    out = src[:first] + new + src[last:]
    out = re.sub(r"(        fun forLang\(lang: AppLang, systemLanguage: String\): MobileStrings \{\n).*?\n        \}\n",
                 r"\1            val resolved = if (lang == AppLang.System) AppLang.entries.firstOrNull { it.code == systemLanguage } ?: AppLang.English else lang\n"
                 r'            return I18n.cached("MobileStrings", resolved) { build(resolved) }\n        }\n', out, count=1, flags=re.S)
    out = out.replace("compositionLocalOf { MobileStrings.EN }", "compositionLocalOf { MobileStrings.forLang(AppLang.English, \"en\") }")
    out = add_imports(out, rel)
    (ROOT / KT / rel).write_text(out, encoding="utf-8", newline="\n")


# ---------------------------------------------------------------- XML

PLURAL_QUANTITIES = {
    "en": ["one", "other"],
    "fr": ["one", "many", "other"],
    "es": ["one", "many", "other"],
    "ar": ["zero", "one", "two", "few", "many", "other"],
}
LEGACY_NOTE ="Ressources Android historiques (manifeste, canaux de notification) : inchangées."


def xml_text(s):
    s = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    s = s.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"')
    s = s.replace("\n", "\\n").replace("\t", "\\t").replace("\r", "\\r")
    if s[:1] in ("@", "?"):
        s = "\\" + s
    # aapt2 réduit les blancs : on met entre guillemets les textes à blancs significatifs
    if s != s.strip(" ") or "  " in s or s.startswith("\\n") or s.endswith("\\n") and False:
        s = '"' + s.replace("\\'", "'") + '"'
    return s


FMT_OK = re.compile(r"%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[sdfxXeEgGcb]|%%|%n")


def format_flag(text):
    """formatted="false" si le texte contient un % que aapt2 ne tient pas pour un format valide."""
    rest = FMT_OK.sub("", text)
    if "%" in rest:
        return True
    specs = re.findall(r"%(\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[sdfxXeEgGcb]", text)
    positional = [s for s in specs if s]
    return len(specs) > 1 and len(positional) != len(specs)


def read_legacy(base, path):
    try:
        raw = git_show(base, path)
    except subprocess.CalledProcessError:
        return []
    return re.findall(r"^\s*(<string .*?</string>)\s*$", raw, re.M)


def write_xml(base):
    for lg in LANGS:
        folder = RES / ("values" if lg == "en" else f"values-{lg}")
        folder.mkdir(parents=True, exist_ok=True)
        legacy = read_legacy(base, f"android-native/app/src/main/res/{'values' if lg == 'en' else 'values-' + lg}/strings.xml") \
            if lg in ("en", "fr") else []
        out = ['<?xml version="1.0" encoding="utf-8"?>',
               "<!-- Généré par tools/i18n/migrate.py puis maintenu à la main. Source : values/ (anglais, repli). -->",
               '<resources xmlns:tools="http://schemas.android.com/tools">']
        if legacy:
            out.append(f"    <!-- {LEGACY_NOTE} -->")
            out += ["    " + l for l in legacy]
            CAT.legacy = getattr(CAT, "legacy", {})
            CAT.legacy[lg] = re.findall(r'name="([^"]+)"', "".join(legacy))
        for rel, keys in CAT.sections:
            lines = []
            for k in keys:
                e = CAT.entries[k]
                v = e["values"].get(lg)
                if v is None:
                    continue
                if e["kind"] == "array":
                    items = "".join(f"\n        <item>{xml_text(t)}</item>" for t in v)
                    lines.append(f'    <string-array name="{k}">{items}\n    </string-array>')
                elif e["kind"] == "string":
                    fl = ' formatted="false"' if format_flag(v) else ""
                    lines.append(f'    <string name="{k}"{fl}>{xml_text(v)}</string>')
                else:
                    # Quantités CLDR de la langue complétées avec « other » : même texte qu'avant, lint MissingQuantity content
                    full = {q: v.get(q, v["other"]) for q in PLURAL_QUANTITIES[lg]}
                    items = "".join(f'\n        <item quantity="{q}">{xml_text(t)}</item>' for q, t in full.items())
                    # « one » sans nombre (« Enregistrement programmé ») : voulu, 0 et 1 sont le même cas en français
                    ign = ' tools:ignore="ImpliedQuantity"' if "%" not in full.get("one", "%") else ""
                    lines.append(f'    <plurals name="{k}"{ign}>{items}\n    </plurals>')
            if lines:
                out.append(f"\n    <!-- {rel} -->")
                out += lines
        out.append("</resources>\n")
        (folder / "strings.xml").write_text("\n".join(out), encoding="utf-8", newline="\n")


def write_whitelist():
    """Clés volontairement absentes de values-es / values-ar (identiques à l'anglais dans l'ancien code) : liste blanche du test."""
    lines = ["# Clés absentes d'une langue officielle car identiques à l'anglais (le repli natif affiche l'anglais).",
             "# Format : langue|clé. Générée par tools/i18n/migrate.py ; à compléter à la main pour un nom propre ou un sigle."]
    for lg in ("fr", "es", "ar"):
        # ressources historiques jamais traduites dans cette langue : repli anglais, comme avant la migration
        for k in CAT.legacy["en"]:
            if k not in CAT.legacy.get(lg, []):
                lines.append(f"{lg}|{k}")
        for k, e in CAT.entries.items():
            if lg not in e["values"]:
                lines.append(f"{lg}|{k}")
    out = ROOT / "android-native/app/src/test/resources/i18n-identical-whitelist.txt"
    out.write_text(chr(10).join(lines) + chr(10), encoding="utf-8", newline=chr(10))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="origin/main")
    ap.add_argument("--report", default=str(ROOT / "tools/i18n/report.json"))
    a = ap.parse_args()
    for rel, prefix in FILES.items():
        process_file(rel, prefix, a.base)
    process_strings_kt(a.base)
    process_mobile_kt(a.base)
    import manual
    manual.apply(CAT, ROOT, KT)
    write_xml(a.base)
    write_whitelist()
    import whitelists
    print('listes blanches (identiques, sans arabe) :', whitelists.write(RES, ROOT / 'android-native/app/src/test/resources'))
    n = {lg: sum(1 for e in CAT.entries.values() if lg in e["values"]) for lg in LANGS}
    rep = {
        "base": a.base,
        "keys": len(CAT.entries),
        "plurals": sum(1 for e in CAT.entries.values() if e["kind"] == "plural"),
        "per_lang": n,
        "default_to_english_es": CAT.defaulted["es"],
        "default_to_english_ar": CAT.defaulted["ar"],
        "reprises_manuelles_traitees_par_manual_py": CAT.manual,
    }
    Path(a.report).write_text(json.dumps(rep, ensure_ascii=False, indent=1), encoding="utf-8")
    print(json.dumps({k: (v if not isinstance(v, list) else len(v)) for k, v in rep.items()}, ensure_ascii=False))
    print(len(CAT.manual), "construction(s) reprise(s) à la main dans manual.py")


if __name__ == "__main__":
    main()
