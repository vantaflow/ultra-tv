#!/usr/bin/env python3
"""Génère les rasters de l'icône Ultra TV (PNG de repli mdpi→xxxhdpi, bannière TV) depuis la géométrie de ultratv-icon.svg.
Les VectorDrawables (premier plan adaptatif, monochrome) sont écrits à la main dans res/drawable. Police : Sora (déjà dans l'app).
Usage : python3 tools/icon/make_icons.py"""
import os
from PIL import Image, ImageDraw, ImageFont
ROOT = os.path.join(os.path.dirname(__file__), "../../app/src/main/res")
FONT = os.path.join(ROOT, "font/sora.ttf")
BG, FG, RED = (10, 10, 12), (245, 245, 247), (217, 30, 43)

def mark(size, ss=4, rounded=True, round_mask=False, bg=BG):
    """Icône complète en `size` px (suréchantillonnée)."""
    n = size * ss; k = n / 512
    im = Image.new("RGBA", (n, n), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    if round_mask: d.ellipse([0, 0, n - 1, n - 1], fill=bg)
    elif rounded: d.rounded_rectangle([0, 0, n - 1, n - 1], radius=116 * k, fill=bg)
    else: d.rectangle([0, 0, n, n], fill=bg)
    # écran : contour de 28, rx 40 (le trait est centré sur le rectangle 96,120 320×216)
    d.rounded_rectangle([(96 - 14) * k, (120 - 14) * k, (416 + 14) * k, (336 + 14) * k], radius=54 * k, fill=FG)
    d.rounded_rectangle([(96 + 14) * k, (120 + 14) * k, (416 - 14) * k, (336 - 14) * k], radius=26 * k, fill=bg)
    d.polygon([(224 * k, 188 * k), (224 * k, 268 * k), (294 * k, 228 * k)], fill=RED)
    d.line([(196 * k, 392 * k), (316 * k, 392 * k)], fill=FG, width=int(28 * k))
    for x in (196, 316): d.ellipse([(x - 14) * k, (392 - 14) * k, (x + 14) * k, (392 + 14) * k], fill=FG)
    return im.resize((size, size), Image.LANCZOS)

def legacy(size, round_):
    # Les icônes de repli occupent toute la grille : on réduit légèrement le motif dans le disque.
    return mark(size, round_mask=round_)

for dpi, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
    out = os.path.join(ROOT, f"mipmap-{dpi}"); os.makedirs(out, exist_ok=True)
    legacy(px, False).save(os.path.join(out, "ic_launcher.png"), optimize=True)
    legacy(px, True).save(os.path.join(out, "ic_launcher_round.png"), optimize=True)

# Bannière Android TV : 320×180 dp rendus en xhdpi (640×360 px), fond rouge, icône + « ULTRA TV » en Sora.
S = 2
ban = Image.new("RGBA", (320 * S, 180 * S), RED + (255,)); ban.alpha_composite(mark(int(512 * 0.156 * S)), (30 * S, 50 * S))
d = ImageDraw.Draw(ban)
def sora(w, size):
    f = ImageFont.truetype(FONT, size * S); f.set_variation_by_axes([w]); return f
f1, f2 = sora(800, 34), sora(600, 34)
x = 122 * S; y = 102 * S
d.text((x, y), "ULTRA", font=f1, fill=(255, 255, 255), anchor="ls")
d.text((x + d.textlength("ULTRA", font=f1), y), " TV", font=f2, fill=(255, 228, 230), anchor="ls")
os.makedirs(os.path.join(ROOT, "drawable-xhdpi"), exist_ok=True)
ban.convert("RGB").save(os.path.join(ROOT, "drawable-xhdpi/banner.png"), optimize=True)
print("ok")
