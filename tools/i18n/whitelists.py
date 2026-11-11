"""Listes blanches des tests de traduction, calculées sur les strings.xml écrits.

 - i18n-same-as-english-whitelist.txt : valeurs d'une langue officielle identiques à l'anglais (noms propres, sigles,
   formats, mots communs aux langues). Format `langue|clé`.
 - i18n-no-arabic-whitelist.txt : valeurs arabes sans aucune lettre arabe (codes, formats, sigles). Format `ar|clé`.

Générées au moment de la migration ; ensuite elles se maintiennent à la main (le test refuse les entrées périmées).
"""
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ARABIC = re.compile("[؀-ۿ]")


def load(path):
    out = {}
    if not path.exists():
        return out
    for e in ET.parse(path).getroot():
        n = e.get("name")
        if e.tag == "string":
            out[n] = [("", e.text or "")]
        elif e.tag == "plurals":
            out[n] = [(i.get("quantity"), i.text or "") for i in e]
        elif e.tag == "string-array":
            out[n] = [(str(k), i.text or "") for k, i in enumerate(e)]
    return out


def write(res: Path, test_res: Path):
    en = load(res / "values/strings.xml")
    same, noar = [], []
    for lang in ("fr", "es", "ar"):
        tr = load(res / f"values-{lang}/strings.xml")
        for k, items in tr.items():
            if k in en and items == en[k]:
                same.append(f"{lang}|{k}")
            if lang == "ar" and not all(ARABIC.search(t) for _, t in items if t):
                noar.append(f"ar|{k}")
    head = "# {}\n# Format : langue|clé. Générée par tools/i18n/whitelists.py ; maintenue à la main ensuite.\n"
    (test_res / "i18n-same-as-english-whitelist.txt").write_text(
        head.format("Valeurs identiques à l'anglais (volontairement).") + "\n".join(same) + "\n", encoding="utf-8", newline="\n")
    (test_res / "i18n-no-arabic-whitelist.txt").write_text(
        head.format("Valeurs arabes sans lettre arabe (codes, formats, sigles).") + "\n".join(noar) + "\n", encoding="utf-8", newline="\n")
    return len(same), len(noar)
