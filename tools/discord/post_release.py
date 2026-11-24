#!/usr/bin/env python3
"""Annonce d'une version publiée sur Discord (webhooks), à partir de CHANGELOG.md.

- #mises-a-jour (DISCORD_WEBHOOK_UPDATES) : toutes les rubriques de la version (sauf « Technique »).
- #new-features (DISCORD_WEBHOOK_FEATURES) : seulement « Ajouté », s'il y en a.

Usage : post_release.py <tag> [--dry-run]   (tag : v1.3.77, desktop-v1.2.78)
Aucune dépendance (bibliothèque standard). Un webhook absent est simplement ignoré.
"""
import json, os, re, sys, urllib.request

DOWNLOAD = "https://ultratv.dpdns.org/download"
ICONS = {"Ajouté": "✨", "Modifié": "🔧", "Corrigé": "🐛", "Bureau": "🖥️", "Sécurité": "🔒", "Retiré": "🗑️"}
SKIP = {"Technique"}


def section(changelog: str, tag: str):
    """(titre, {rubrique: [puces]}, [puces anglaises]) de la version du tag, ou None."""
    if tag.startswith("desktop-v"):
        ver, head, title = tag[len("desktop-v"):], r"## \[Bureau " + re.escape(tag[len("desktop-v"):]) + r"\]", "🖥️ Ultra TV bureau " + tag[len("desktop-v"):]
    elif tag.startswith("v"):
        ver, head, title = tag[1:], r"## \[" + re.escape(tag[1:]) + r"\]", "📺 Ultra TV Android " + tag[1:]
    else:
        return None
    m = re.search(r"^" + head + r".*$", changelog, re.M)
    if not m:
        return None
    end = re.search(r"^## ", changelog[m.end():], re.M)
    body = changelog[m.end(): m.end() + end.start() if end else len(changelog)]
    parts, english, cur = {}, [], None
    for line in body.splitlines():
        h = re.match(r"^###\s+(.*)$", line)
        if h:
            cur = h.group(1).strip()
            continue
        b = re.match(r"^- (.*)$", line)
        if not b or cur is None or cur in SKIP:
            continue
        (english if cur.lower().startswith("in english") else parts.setdefault(cur, [])).append(b.group(1).strip())
    return title, parts, english


def short(text: str, limit: int = 190) -> str:
    """Première phrase d'une entrée, coupée proprement (les entrées du CHANGELOG sont longues)."""
    text = re.sub(r"\s+", " ", text)
    text = re.sub(r"\s*\([^()]*\)", "", text)          # parenthèses (détails, numéros d'issue)
    text = re.sub(r"`([^`]*)`", r"\1", text)
    cut = re.split(r"(?<=[.;])\s", text, maxsplit=1)[0].rstrip(" .;:")
    if len(cut) > limit:
        cut = cut[:limit].rsplit(" ", 1)[0] + "…"
    return cut


def bullets(items, budget: int) -> str:
    out, used = [], 0
    for i, it in enumerate(items):
        line = "• " + short(it)
        if used + len(line) + 1 > budget:
            out.append(f"• … et {len(items) - i} autre(s)")
            break
        out.append(line)
        used += len(line) + 1
    return "\n".join(out)


def build(tag: str, url: str, changelog: str):
    s = section(changelog, tag)
    if not s:
        return None, None
    title, parts, english = s
    desc = []
    for name, items in parts.items():
        desc.append(f"**{ICONS.get(name, '•')} {name}**\n" + bullets(items, 1100))
    if english:
        desc.append("**🇬🇧 In English**\n" + bullets(english, 900))
    desc.append(f"⬇️ [Télécharger / Download]({DOWNLOAD}) · [Notes complètes]({url})")
    updates = {"username": "Ultra TV", "embeds": [{"title": title, "url": url, "color": 0xE50914, "description": "\n\n".join(desc)[:4000]}]}
    features = None
    if parts.get("Ajouté"):
        features = {"username": "Ultra TV", "embeds": [{
            "title": "🚀 Nouveautés · " + title.split(" ", 1)[1], "url": url, "color": 0x5865F2,
            "description": (bullets(parts["Ajouté"], 3000) + f"\n\n⬇️ [Télécharger / Download]({DOWNLOAD})\n💡 Une idée ? #ideas-and-suggestions")[:4000],
        }]}
    return updates, features


def post(hook: str, payload: dict):
    req = urllib.request.Request(hook, data=json.dumps(payload).encode(), headers={"content-type": "application/json", "user-agent": "ultratv-release-bot"})
    with urllib.request.urlopen(req, timeout=20) as r:
        print("Discord :", r.status)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    dry = "--dry-run" in sys.argv
    tag = args[0]
    url = os.environ.get("RELEASE_URL") or f"https://github.com/vantaflow/ultra-tv/releases/tag/{tag}"
    changelog = open(os.environ.get("CHANGELOG", "CHANGELOG.md"), encoding="utf-8").read()
    updates, features = build(tag, url, changelog)
    if not updates:
        print(f"Aucune section CHANGELOG pour {tag} : rien à annoncer")
        return
    for env, payload in (("DISCORD_WEBHOOK_UPDATES", updates), ("DISCORD_WEBHOOK_FEATURES", features)):
        if not payload:
            continue
        if dry:
            print(env, json.dumps(payload, ensure_ascii=False, indent=1))
        elif os.environ.get(env):
            post(os.environ[env], payload)
        else:
            print(f"{env} absent : ignoré")


if __name__ == "__main__":
    main()
