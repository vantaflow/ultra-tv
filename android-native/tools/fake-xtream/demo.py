#!/usr/bin/env python3
"""Source de DÉMONSTRATION (données 100 % synthétiques) pour les captures publiques.

  python3 demo.py [port]        -> http://10.0.2.2:PORT depuis l'émulateur, identifiants demo / demo
  DEMO_MEDIA=/chemin/clip.ts    -> fichier servi comme flux (voir README : ffmpeg -f lavfi -i gradients ...)

Noms de chaînes et de films fictifs, visuels générés (dégradés + titre). Aucun contenu réel.
"""
import html, io, json, os, sys, time, hashlib, random
from urllib.parse import urlparse, parse_qs
from PIL import Image, ImageDraw, ImageFont
import server

FONT = os.environ.get("DEMO_FONT", os.path.join(os.path.dirname(__file__), "../../app/src/main/res/font/sora.ttf"))
PALETTES = [((217, 30, 43), (40, 8, 20)), ((38, 99, 235), (8, 14, 40)), ((16, 150, 110), (6, 30, 30)), ((234, 150, 20), (50, 20, 8)),
            ((139, 70, 220), (24, 10, 48)), ((220, 60, 140), (40, 10, 30)), ((20, 160, 200), (6, 24, 44)), ((110, 120, 130), (18, 20, 26))]
LIVE_CATS = [("1", "Actualités"), ("2", "Sport"), ("3", "Cinéma"), ("4", "Jeunesse"), ("5", "Documentaires"), ("6", "Musique")]
CHANNELS = {
    "1": ["Atlas Info", "Rif 24", "Monde Direct", "Horizon Journal", "Cap Actu"],
    "2": ["Horizon Sport 1", "Horizon Sport 2", "Arène Football", "Tennis Club", "Grand Prix TV", "Basket Max"],
    "3": ["Cinéma Lumière", "Cinéma Noir", "Écran Classique", "Frisson Ciné", "Comédie Club"],
    "4": ["Petits Pas", "Cartoon Parc", "Junior Kids", "Aventure Jeunesse"],
    "5": ["Terre Sauvage", "Océans", "Histoire Vivante", "Science & Nous", "Voyage Planète"],
    "6": ["Hit Radio TV", "Jazz Live", "Électro Nuit", "Chanson Française"],
}
VOD_CATS = [("11", "Action"), ("12", "Comédie"), ("13", "Science-fiction"), ("14", "Drame")]
MOVIES = {
    "11": ["Dernier Rempart", "Ligne de Feu", "Opération Aube", "Course Folle", "Cap sur l'Orage", "Le Convoi"],
    "12": ["Un Été à Tanger", "Les Voisins du Dessus", "Mariage en Vue", "Tout Sauf Lundi", "Café Crème", "La Grande Évasion Douce"],
    "13": ["Orbite Zéro", "Les Dormeurs d'Argent", "Station Mirage", "Signal 7", "Horizon Perdu", "Neptune Rouge"],
    "14": ["Les Jours Blancs", "Le Poids du Sable", "Retour au Port", "Lettres d'Hiver", "L'Atelier", "Mémoire Vive"],
}
SERIES_CATS = [("21", "Policier"), ("22", "Comédie")]
SERIES = {"21": ["Brigade Nord", "Quai 9", "Enquêtes Sauvages", "Dossier Alpha"], "22": ["Colocs", "Open Space", "Les Deux Étages", "Pause Café"]}
SYN = ("Un récit original et entièrement fictif tourné pour la démonstration : des personnages attachants, un rythme soutenu et un dénouement "
       "que personne n'attend. Aucune image réelle n'est utilisée dans cette fiche.")
CAST = "Samir Lahlou, Inès Marchand, Yanis Berrada, Clara Dufour"
PROGS = ["Journal", "Le Grand Match", "Magazine de la semaine", "Film du soir", "Documentaire", "Dessins animés", "Concert", "Météo", "Débat", "Série du jour"]

def font(size):
    try: return ImageFont.truetype(FONT, size)
    except Exception: return ImageFont.load_default()

def grad(w, h, seed):
    a, b = PALETTES[seed % len(PALETTES)]
    img = Image.new("RGB", (w, h)); px = img.load()
    for y in range(h):
        t = y / max(h - 1, 1)
        for x in range(0, w):
            u = (t * 0.8 + x / w * 0.2)
            px[x, y] = tuple(int(a[i] * (1 - u) + b[i] * u) for i in range(3))
    d = ImageDraw.Draw(img, "RGBA"); r = random.Random(seed)
    for _ in range(5):
        cx, cy, rad = r.randint(0, w), r.randint(0, h), r.randint(h // 6, h // 2)
        d.ellipse([cx - rad, cy - rad, cx + rad, cy + rad], fill=(255, 255, 255, 14))
    return img, d

def wrap(d, text, f, maxw):
    lines, cur = [], ""
    for wd in text.split():
        t = (cur + " " + wd).strip()
        if d.textlength(t, font=f) <= maxw: cur = t
        else: lines.append(cur); cur = wd
    return lines + [cur]

def art(kind, title, seed):
    if kind == "logo":
        img, d = grad(256, 256, seed); f = font(96); ini = "".join(w[0] for w in title.split()[:2]).upper()
        d.text((128, 128), ini, font=f, fill="white", anchor="mm")
    elif kind == "backdrop":
        img, d = grad(1280, 720, seed)
    else:
        img, d = grad(400, 600, seed); f = font(46)
        y = 330
        for ln in wrap(d, title, f, 340):
            d.text((30, y), ln, font=f, fill="white"); y += 58
        d.rectangle([30, 300, 110, 306], fill=(255, 255, 255, 220))
    b = io.BytesIO(); img.save(b, "JPEG", quality=84); return b.getvalue()

def ids(prefix, base, names):
    out, n = [], 0
    for cat, lst in names.items():
        for nm in lst: out.append((base + n, cat, nm)); n += 1
    return out

LIVE = ids("l", 1000, CHANNELS); MOV = ids("m", 5000, MOVIES); SER = ids("s", 8000, SERIES)

class Demo(server.H):
    def do_GET(self):
        u = urlparse(self.path); q = parse_qs(u.query); host = self.headers.get("Host", "10.0.2.2:8099")
        base = f"http://{host}"
        if u.path.startswith("/art/"):
            _, _, kind, sid = u.path.split("/")[:4]; sid = int(sid.split(".")[0])
            allx = {i: (n, k) for i, c, n in LIVE for k in ["l"]} | {i: (n, "m") for i, c, n in MOV} | {i: (n, "s") for i, c, n in SER}
            title = allx.get(sid, ("Démo", "m"))[0]
            return self.send(200, art(kind, title, sid), "image/jpeg")
        if u.path == "/xmltv.php":
            t0 = int(time.time()) // 3600 * 3600 - 3 * 3600; out = ['<?xml version="1.0" encoding="UTF-8"?><tv>']
            for i, c, n in LIVE:
                out.append(f'<channel id="e{i}"><display-name>{html.escape(n)}</display-name></channel>')
                r = random.Random(i); t = t0
                while t < t0 + 30 * 3600:
                    dur = r.choice([1800, 3600, 3600, 5400, 7200]); title = r.choice(PROGS) + " " + r.choice(["", "spécial", "n°%d" % r.randint(2, 40)])
                    s = time.strftime("%Y%m%d%H%M%S +0000", time.gmtime(t)); e = time.strftime("%Y%m%d%H%M%S +0000", time.gmtime(t + dur))
                    out.append(f'<programme start="{s}" stop="{e}" channel="e{i}"><title>{html.escape(title.strip())}</title><desc>{SYN}</desc></programme>'); t += dur
            out.append("</tv>"); return self.send(200, "".join(out).encode(), "text/xml")
        if u.path == "/player_api.php":
            a = q.get("action", [""])[0]
            if a == "":
                return self.send(200, json.dumps({"user_info": {"auth": 1, "status": "Active", "max_connections": "1"}, "server_info": {}}).encode(), "application/json")
            if a == "get_live_categories": d = [{"category_id": i, "category_name": n} for i, n in LIVE_CATS]
            elif a == "get_vod_categories": d = [{"category_id": i, "category_name": n} for i, n in VOD_CATS]
            elif a == "get_series_categories": d = [{"category_id": i, "category_name": n} for i, n in SERIES_CATS]
            elif a == "get_live_streams":
                d = [{"num": k + 1, "name": n, "stream_type": "live", "stream_id": i, "stream_icon": f"{base}/art/logo/{i}.jpg", "epg_channel_id": f"e{i}",
                      "category_id": c, "tv_archive": 1 if k % 3 == 0 else 0, "tv_archive_duration": 3} for k, (i, c, n) in enumerate(LIVE)]
            elif a == "get_vod_streams":
                d = [{"num": k + 1, "name": n, "stream_id": i, "stream_icon": f"{base}/art/poster/{i}.jpg", "rating": "%.1f" % (6.2 + (i % 27) / 10),
                      "added": "1700000000", "category_id": c, "container_extension": "mp4"} for k, (i, c, n) in enumerate(MOV)]
            elif a == "get_series":
                d = [{"num": k + 1, "name": n, "series_id": i, "cover": f"{base}/art/poster/{i}.jpg", "plot": SYN, "releaseDate": "2023-03-01", "rating": "8.%d" % (i % 9), "category_id": c}
                     for k, (i, c, n) in enumerate(SER)]
            elif a == "get_series_info":
                sid = int(q.get("series_id", ["8000"])[0])
                eps = {str(s): [{"id": str(sid * 100 + s * 10 + e), "episode_num": e, "title": f"Épisode {e}", "container_extension": "mp4",
                                 "info": {"plot": SYN, "movie_image": f"{base}/art/backdrop/{sid}.jpg", "duration": "00:42:00"}} for e in range(1, 7)] for s in (1, 2)}
                return self.send(200, json.dumps({"episodes": eps, "info": {"name": "Série", "plot": SYN, "cover": f"{base}/art/poster/{sid}.jpg"}}).encode(), "application/json")
            elif a == "get_vod_info":
                vid = int(q.get("vod_id", ["5000"])[0]); nm = next((n for i, c, n in MOV if i == vid), "Démo")
                info = {"name": nm, "plot": SYN, "cast": CAST, "director": "Leïla Haddad", "genre": "Action / Aventure", "duration": "1:52:00", "duration_secs": 6720,
                        "releasedate": "2024-05-15", "rating": "7.4", "movie_image": f"{base}/art/poster/{vid}.jpg", "backdrop_path": [f"{base}/art/backdrop/{vid}.jpg"]}
                return self.send(200, json.dumps({"info": info, "movie_data": {"stream_id": vid, "name": nm, "container_extension": "mp4"}}).encode(), "application/json")
            else: d = []
            return self.send(200, json.dumps(d).encode(), "application/json")
        if u.path.startswith(("/live/", "/movie/", "/series/", "/timeshift/")):
            return self.media()
        return super().do_GET()

    def media(self):
        path = os.environ.get("DEMO_MEDIA")
        if not path or not os.path.exists(path): return self.send(404, b"", "text/plain")
        size = os.path.getsize(path); rng = self.headers.get("Range"); start, end = 0, size - 1
        if rng and rng.startswith("bytes="):
            a, _, b = rng[6:].partition("-"); start = int(a or 0); end = int(b) if b else size - 1
        ctype = "video/mp2t" if path.endswith(".ts") else "video/mp4"
        self.send_response(206 if rng else 200); self.send_header("Content-Type", ctype); self.send_header("Accept-Ranges", "bytes")
        self.send_header("Content-Length", str(end - start + 1))
        if rng: self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
        self.end_headers()
        try:
            with open(path, "rb") as f:
                f.seek(start); left = end - start + 1
                while left > 0:
                    chunk = f.read(min(65536, left))
                    if not chunk: break
                    self.wfile.write(chunk); left -= len(chunk)
        except (BrokenPipeError, ConnectionResetError): pass

if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8099
    server.ThreadingHTTPServer(("0.0.0.0", port), Demo).serve_forever()
