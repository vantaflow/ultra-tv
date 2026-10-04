#!/usr/bin/env python3
"""Prépare les captures publiques à partir des captures brutes 1920×1080 de l'émulateur (source de démonstration SYNTHÉTIQUE
android-native/tools/fake-xtream/demo.py, jamais une vraie source) : 1280×720 en PNG, plus la bannière du README.

  python3 make_screens.py RAW_DIR OUT_DIR LANG        RAW_DIR contient LANG-home.png, LANG-live.png… (voir docs/screenshots/README)
"""
import os, sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter
RAW, OUT, LANG = sys.argv[1:4]
RES = os.path.join(os.path.dirname(__file__), "../../app/src/main/res")
SORA = os.path.join(RES, "font/sora.ttf")
os.makedirs(OUT, exist_ok=True)
NAMES = {"home": "home", "home-light": "home-light", "live": "live", "guide": "guide", "detail": "detail", "player": "player",
         "settings": "settings", "profiles": "profiles", "languages": "languages"}
for src, dst in NAMES.items():
    im = Image.open(os.path.join(RAW, f"{LANG}-{src}.png")).convert("RGB").resize((1280, 720), Image.LANCZOS)
    im.save(os.path.join(OUT, dst + ".png"), optimize=True)

# Bannière 1600×520 : icône + titre + accroche à gauche (à droite en arabe), capture « Direct » à demi cadrée de l'autre côté.
TAG = {"fr": ("Lecteur IPTV natif", "Android TV et Google TV"), "en": ("Native IPTV player", "for Android TV and Google TV"),
       "ar": ("مشغّل بث تلفزيوني أصلي", "لأندرويد تي في وجوجل تي في")}[LANG]
W, H = 1600, 520
bg = Image.new("RGB", (W, H), (10, 10, 12)); d = ImageDraw.Draw(bg)
for y in range(H):  # léger dégradé vertical
    c = int(10 + 10 * y / H); d.line([(0, y), (W, y)], fill=(c, c, c + 2))
rtl = LANG == "ar"
shot = Image.open(os.path.join(OUT, "live.png")).convert("RGB")
sw = 780; sh = int(shot.height * sw / shot.width)
shot = shot.resize((sw, sh), Image.LANCZOS)
x_shot = 40 if rtl else W - sw - 60
frame = Image.new("RGB", (sw + 4, sh + 4), (60, 60, 66)); frame.paste(shot, (2, 2))
bg.paste(frame, (x_shot, 110))
icon = Image.open(os.path.join(RES, "mipmap-xxxhdpi/ic_launcher.png")).convert("RGBA").resize((120, 120), Image.LANCZOS)
def font(path, size, wght=None, layout=None):
    f = ImageFont.truetype(path, size, layout_engine=layout) if layout is not None else ImageFont.truetype(path, size)
    if wght: f.set_variation_by_axes([wght])
    return f
if rtl:
    GEEZA = "/System/Library/Fonts/SFArabic.ttf"  # SF Arabic : arabe + latin
    ft = font(GEEZA, 92, layout=ImageFont.Layout.RAQM); fg = font(GEEZA, 40, layout=ImageFont.Layout.RAQM)
    right = W - 90
    bg.paste(icon, (right - 120, 40), icon)
    d.text((right, 255), "Ultra TV", font=font(SORA, 100, 800), fill=(255, 255, 255), anchor="ra")
    d.text((right, 360), TAG[0], font=fg, fill=(190, 190, 196), anchor="ra", direction="rtl")
    d.text((right, 415), TAG[1], font=fg, fill=(190, 190, 196), anchor="ra", direction="rtl")
else:
    bg.paste(icon, (90, 40), icon)
    d.text((90, 262), "Ultra TV", font=font(SORA, 100, 800), fill=(255, 255, 255), anchor="ls")
    d.text((90, 330), TAG[0], font=font(SORA, 40, 300), fill=(190, 190, 196), anchor="ls")
    d.text((90, 385), TAG[1], font=font(SORA, 40, 300), fill=(190, 190, 196), anchor="ls")
bg.save(os.path.join(OUT, "banner.png"), optimize=True)
print("ok", LANG)
