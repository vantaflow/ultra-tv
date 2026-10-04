#!/usr/bin/env python3
"""Génère site/index.html (FR) et site/en/index.html (EN) depuis page.tpl.html, et les images du site depuis docs/screenshots.

  python3 tools/site/build.py

Le gabarit contient les textes FR/EN côte à côte (<span lang="fr">…</span><span lang="en">…</span>) : chaque page ne garde que sa langue.
Images : WebP (qualité 80) + PNG de repli (<picture>), par langue, dans site/img/{fr,en,ar}/.
"""
import os, re, shutil
from PIL import Image
ROOT = os.path.join(os.path.dirname(__file__), "../..")
SITE = os.path.join(ROOT, "site"); SHOTS = os.path.join(ROOT, "docs/screenshots")
ALT = {
 "home": ("Écran d'accueil d'Ultra TV avec une affiche à la une et la rangée Chaînes en direct", "Ultra TV home screen with a featured poster and the Live TV row"),
 "live": ("Écran Direct : catégories à gauche, chaînes avec logos et programme en cours", "Live TV screen: categories on the left, channels with logos and the current programme"),
 "guide": ("Guide des programmes : grille horaire avec la ligne du moment présent", "Programme guide: time grid with the current-time line"),
 "detail": ("Fiche d'un film : visuel, informations, bouton Lecture et distribution", "Movie details: artwork, information, Play button and cast"),
 "player": ("Lecteur plein écran avec les commandes superposées", "Full-screen player with overlaid controls"),
 "phone": ("Ultra TV sur smartphone Android", "Ultra TV on an Android smartphone"),
 "tablet": ("Ultra TV sur tablette Android : menu latéral, liste et fiche", "Ultra TV on an Android tablet: side rail, list and details"),
 "desktop": ("Ultra TV sur Windows et macOS", "Ultra TV on Windows and macOS"),
 "cloud": ("Tableau de bord cloud : appareils et sources", "Cloud dashboard: devices and sources"),
 "ar": ("Accueil en arabe : interface en miroir, menu latéral à droite", "Home screen in Arabic: mirrored interface, side rail on the right"),
}
PAGES = {
 "fr": dict(out="index.html", ROOT="", LANG="fr", ALT="en", ALTCODE="EN", ALTHREF="en/", ALTLABEL="English version", OGLOC="fr_FR",
            CANON="https://khalilbenaz.github.io/ultra-tv/", i=0, THEMELABEL="Thème clair ou sombre",
            TITLE="Ultra TV — lecteur IPTV pour TV, smartphone, tablette et ordinateur",
            DESC="Ultra TV est un lecteur IPTV pour Android TV, smartphone, tablette, Windows et macOS : Xtream Codes, M3U, guide, replay, timeshift, deux moteurs de lecture, synchronisation cloud, réglages automatiques."),
 "en": dict(out="en/index.html", ROOT="../", LANG="en", ALT="fr", ALTCODE="FR", ALTHREF="../", ALTLABEL="Version française", OGLOC="en_US",
            CANON="https://khalilbenaz.github.io/ultra-tv/en/", i=1, THEMELABEL="Light or dark theme",
            TITLE="Ultra TV — IPTV player for TV, smartphone, tablet and desktop",
            DESC="Ultra TV is an IPTV player for Android TV, smartphone, tablet, Windows and macOS: Xtream Codes, M3U, guide, replay, timeshift, two playback engines, cloud sync, automatic tuning."),
}
tpl = open(os.path.join(os.path.dirname(__file__), "page.tpl.html"), encoding="utf-8").read()
for lang, P in PAGES.items():
    h = tpl
    h = re.sub(r'<span lang="fr">(.*?)</span><span lang="en">(.*?)</span>', lambda m: m.group(1 + P["i"]), h, flags=re.S)
    h = re.sub(r"\{\{ALT:(\w+)\}\}", lambda m: ALT[m.group(1)][P["i"]].replace('"', "&quot;"), h)
    for k in ("ROOT", "LANG", "ALTCODE", "ALTHREF", "ALTLABEL", "ALT", "OGLOC", "CANON", "THEMELABEL", "TITLE", "DESC"):
        h = h.replace("{{%s}}" % k, P[k])
    assert "{{" not in h, re.findall(r"\{\{[^}]*\}\}", h)[:3]
    out = os.path.join(SITE, P["out"]); os.makedirs(os.path.dirname(out), exist_ok=True)
    open(out, "w", encoding="utf-8").write(h)
# images
for lang, names in {"fr": ["home", "live", "guide", "detail", "player"], "en": ["home", "live", "guide", "detail", "player"], "ar": ["home"]}.items():
    d = os.path.join(SITE, "img", lang); os.makedirs(d, exist_ok=True)
    for n in names:
        src = os.path.join(SHOTS, lang, n + ".png"); im = Image.open(src).convert("RGB")
        im.save(os.path.join(d, n + ".webp"), "WEBP", quality=80, method=6)
        im.quantize(colors=192, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.FLOYDSTEINBERG).save(os.path.join(d, n + ".png"), optimize=True)
# captures des autres appareils : (source, nom, largeur max)
EXTRA = lambda l: [
    (f"phone/{l}/home.png", "phone-home", 540), (f"phone/{l}/live.png", "phone-live", 540), (f"phone/{l}/player.png", "phone-player", 540),
    (f"tablet/{l}/live.png", "tablet-live", 1280), (f"desktop/{l}/06-direct.png", "desktop-direct", 1280), (f"desktop/{l}/14-reglages.png", "desktop-settings", 1280),
    ("cloud/devices.png", "cloud-devices", 880), ("cloud/sharing.png", "cloud-sharing", 880)]
for lang in ("fr", "en"):
    d = os.path.join(SITE, "img", lang)
    for src, name, mw in EXTRA(lang):
        im = Image.open(os.path.join(SHOTS, src)).convert("RGB")
        if im.width > mw: im = im.resize((mw, round(im.height * mw / im.width)), Image.Resampling.LANCZOS)
        im.save(os.path.join(d, name + ".webp"), "WEBP", quality=80, method=6)
        im.quantize(colors=192, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.FLOYDSTEINBERG).save(os.path.join(d, name + ".png"), optimize=True)
print("site généré")
