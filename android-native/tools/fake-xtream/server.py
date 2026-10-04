#!/usr/bin/env python3
"""Faux serveur Xtream local pour tester l'application avec des images extrêmes.

Usage : python3 server.py [port]   (l'émulateur Android y accède via http://10.0.2.2:PORT)
Identifiants : user=test  pass=test. Aucun appel réseau externe, aucune donnée réelle.
Images servies sous /img/<cas>.<ext> : 1x1, 50x50, 4000x300, 300x4000, 4000x4000, transparent,
svg, corrompu (octets invalides en .png), 404 (absent), lent (10 s).
"""
import io, json, sys, time, base64
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
from PIL import Image, ImageDraw

CASES = ["1x1", "50x50", "4000x300", "300x4000", "4000x4000", "transparent", "svg", "corrompu", "absent", "lent"]

def png(w, h, color, transparent=False):
    img = Image.new("RGBA" if transparent else "RGB", (w, h), (0, 0, 0, 0) if transparent else color)
    d = ImageDraw.Draw(img)
    d.rectangle([w // 4, h // 4, w * 3 // 4, h * 3 // 4], fill=(255, 255, 255, 255) if not transparent else (217, 30, 43, 255))
    b = io.BytesIO(); img.save(b, "PNG"); return b.getvalue()

IMG = {
    "1x1": png(1, 1, (200, 40, 40)), "50x50": png(50, 50, (40, 120, 200)),
    "4000x300": png(4000, 300, (40, 160, 90)), "300x4000": png(300, 4000, (160, 90, 40)),
    "4000x4000": png(4000, 4000, (120, 40, 160)), "transparent": png(256, 256, (0, 0, 0), True),
}
SVG = b'<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300"><rect width="400" height="300" fill="#d91e2b"/></svg>'

NAMES = ["Sport 1", "## 24/7 ACTION/ADVENTURE RAW 60fps ##", "قناة الرياضة الأولى الممتازة بدون تقطيع", "Спортивный канал высокой чёткости",
         "BEIN SPORTS MAX 4K UHD FULL HD HEVC H265 LIVE EVENT PREMIUM CHANNEL NUMBER ONE WITH A VERY LONG NAME", "Cinéma +", "Info 24", "Jeunesse"]
CATS = [("1", "Sport"), ("2", "Info"), ("3", "Cinéma"), ("4", "Jeunesse")]

def img_url(host, i, ext="png"):
    c = CASES[i % len(CASES)]
    return f"http://{host}/img/{c}.{'svg' if c == 'svg' else 'png'}"

def live(host):
    return [{"num": i + 1, "name": NAMES[i % len(NAMES)] + ("" if i < len(NAMES) else f" {i}"), "stream_type": "live", "stream_id": 1000 + i,
             "stream_icon": img_url(host, i), "epg_channel_id": f"ch{i}", "category_id": CATS[i % 4][0], "tv_archive": 0} for i in range(120)]

def vod(host):
    return [{"num": i, "name": (NAMES[i % len(NAMES)] + f" film {i}"), "stream_id": 5000 + i, "stream_icon": img_url(host, i),
             "rating": "7.%d" % (i % 10), "releaseDate": f"20{10 + i % 15}-01-01", "category_id": CATS[i % 4][0], "container_extension": "mp4"} for i in range(150)]

def series(host):
    return [{"num": i, "name": NAMES[i % len(NAMES)] + f" série {i}", "series_id": 8000 + i, "cover": img_url(host, i), "plot": "Résumé " * 40,
             "releaseDate": "2021-05-01", "rating": "8.1", "category_id": CATS[i % 4][0]} for i in range(60)]

class H(BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def send(self, code, body, ctype):
        self.send_response(code); self.send_header("Content-Type", ctype); self.send_header("Content-Length", str(len(body))); self.end_headers(); self.wfile.write(body)
    def do_GET(self):
        u = urlparse(self.path); q = parse_qs(u.query); host = self.headers.get("Host", "10.0.2.2:8099")
        if u.path.startswith("/img/"):
            name = u.path[5:].rsplit(".", 1)[0]
            if name == "svg": return self.send(200, SVG, "image/svg+xml")
            if name == "corrompu": return self.send(200, b"not an image at all \x00\x01", "image/png")
            if name == "lent": time.sleep(10); return self.send(200, IMG["50x50"], "image/png")
            if name in IMG: return self.send(200, IMG[name], "image/png")
            return self.send(404, b"", "text/plain")
        if u.path == "/xmltv.php":
            now = time.gmtime(); base = time.mktime(now) - 3600
            out = ['<?xml version="1.0"?><tv>']
            for i in range(120):
                out.append(f'<channel id="ch{i}"><display-name>c{i}</display-name></channel>')
                for k in range(8):
                    s = time.strftime("%Y%m%d%H%M%S +0000", time.gmtime(base + k * 3600)); e = time.strftime("%Y%m%d%H%M%S +0000", time.gmtime(base + (k + 1) * 3600))
                    out.append(f'<programme start="{s}" stop="{e}" channel="ch{i}"><title>Programme {k} très long titre pour tester la troncature des textes</title><desc>Description {k}</desc></programme>')
            out.append("</tv>"); return self.send(200, "".join(out).encode(), "text/xml")
        if u.path == "/player_api.php":
            a = q.get("action", [""])[0]
            if a == "": return self.send(200, json.dumps({"user_info": {"auth": 1, "status": "Active"}}).encode(), "application/json")
            data = {"get_live_categories": [{"category_id": i, "category_name": n} for i, n in CATS],
                    "get_vod_categories": [{"category_id": i, "category_name": n} for i, n in CATS],
                    "get_series_categories": [{"category_id": i, "category_name": n} for i, n in CATS],
                    "get_live_streams": live(host), "get_vod_streams": vod(host), "get_series": series(host)}.get(a)
            if a == "get_series_info":
                eps = {str(s): [{"id": str(9000 + s * 10 + e), "episode_num": e, "title": f"Épisode {e} avec un titre vraiment très long", "container_extension": "mp4",
                                 "info": {"plot": "Résumé", "movie_image": img_url(host, e)}} for e in range(1, 7)] for s in (1, 2)}
                return self.send(200, json.dumps({"episodes": eps, "info": {}}).encode(), "application/json")
            if a == "get_vod_info":
                return self.send(200, json.dumps({"info": {"plot": "Synopsis " * 50, "cast": "Acteur Un, Actrice Deux", "movie_image": img_url(host, 0), "backdrop_path": [img_url(host, 4)], "duration": "1:52:00", "genre": "Action"}}).encode(), "application/json")
            return self.send(200, json.dumps(data or []).encode(), "application/json")
        return self.send(404, b"", "text/plain")

if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8099
    ThreadingHTTPServer(("0.0.0.0", port), H).serve_forever()
