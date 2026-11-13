#!/usr/bin/env python3
"""Faux serveur Xtream pour les captures d'écran automatiques (CI, émulateur). Catalogue FICTIF, aucun flux réel.

Usage : python3 mock_xtream.py [port]   (l'émulateur Android le joint à http://10.0.2.2:<port>)
"""
import json
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

NOW = int(time.time())

LIVE_CATS = [("1", "FR | Généralistes"), ("2", "FR | Info"), ("3", "FR | Sport"), ("4", "FR | Jeunesse")]
CHANNELS = [
    ("Le Fil Info", "2"), ("Info 24", "2"), ("Actu Plus", "2"), ("Monde TV", "2"), ("Chaîne Une", "1"), ("Chaîne Deux", "1"),
    ("Canal Rive", "1"), ("Horizon", "1"), ("Prisme", "1"), ("Atlas TV", "1"), ("Arène Sport", "3"), ("Stade 1", "3"),
    ("Stade 2", "3"), ("Piste 24", "3"), ("Kids Zone", "4"), ("Mini TV", "4"), ("Récré", "4"), ("Toon Club", "4"),
]

VOD_CATS = [("10", "Action"), ("11", "Comédie"), ("12", "Drame"), ("13", "Science-fiction")]
MOVIES = [
    "Nuit Blanche", "La Nuit du Phare", "Minuit Pile", "Nuit d'Orage", "Les Nuits Courtes", "Avant la Nuit", "Nuit Polaire",
    "Bleu de Nuit", "Cette Nuit-là", "Contes de la Nuit", "Dernière Nuit", "Une Nuit en Mer", "Nuits de Juin", "Passé Minuit",
    "Quand Tombe la Nuit", "Reine de la Nuit", "Train de Nuit", "Veille de Nuit", "Le Dernier Quai", "Sable Rouge",
    "Les Hautes Terres", "Ligne 12", "Marée Basse", "Contrechamp", "Orbite Basse", "Le Verger", "Terre Brûlée", "Lueur",
    "Au Bord du Monde", "Les Cerfs-Volants", "Cuisine de Nuit", "L'Odyssée Bleue", "Spider-Moth", "Le Phare",
]
SERIES_CATS = [("20", "Séries FR"), ("21", "Séries US")]
SERIES = [
    "Nuits de Garde", "Une Nuit à Rabat", "Nuit Rouge", "Les Veilleurs de Nuit", "Nuit et Jour", "Nuit Fauve", "La Nuit Longue",
    "Quai Ouest", "Les Collines", "Brigade 9", "Hôtel Lumière", "Rive Gauche",
]


def live_streams(cat=None):
    out = []
    for i, (name, c) in enumerate(CHANNELS, start=1):
        if cat and c != cat:
            continue
        out.append({"num": i, "name": f"FR | {name}", "stream_type": "live", "stream_id": 1000 + i, "stream_icon": "",
                    "epg_channel_id": f"ch{i}.fr", "added": str(NOW - i * 3600), "category_id": c, "tv_archive": 0})
    return out


def vod_streams(cat=None):
    out = []
    for i, name in enumerate(MOVIES):
        c = VOD_CATS[i % len(VOD_CATS)][0]
        if cat and c != cat:
            continue
        out.append({"num": i + 1, "name": name, "stream_type": "movie", "stream_id": 2000 + i, "stream_icon": "",
                    "rating": str(5 + (i * 7) % 5), "added": str(NOW - i * 86400), "category_id": c,
                    "container_extension": "mp4", "releaseDate": f"{2010 + (i * 3) % 15}-01-01"})
    return out


def series(cat=None):
    out = []
    for i, name in enumerate(SERIES):
        c = SERIES_CATS[i % len(SERIES_CATS)][0]
        if cat and c != cat:
            continue
        out.append({"num": i + 1, "name": name, "series_id": 3000 + i, "cover": "", "plot": "", "cast": "", "genre": "Drame",
                    "releaseDate": f"{2012 + i % 12}-01-01", "last_modified": str(NOW - i * 86400), "rating": str(6 + i % 4),
                    "category_id": c, "backdrop_path": []})
    return out


def account():
    return {
        "user_info": {"username": "demo", "password": "demo", "auth": 1, "status": "Active", "exp_date": str(NOW + 365 * 86400),
                      "is_trial": "0", "active_cons": "0", "created_at": str(NOW - 86400), "max_connections": "1",
                      "allowed_output_formats": ["m3u8", "ts"]},
        "server_info": {"url": "10.0.2.2", "port": "8080", "server_protocol": "http", "timezone": "UTC",
                        "timestamp_now": NOW, "time_now": time.strftime("%Y-%m-%d %H:%M:%S", time.gmtime(NOW))},
    }


class Handler(BaseHTTPRequestHandler):
    def _send(self, body, ctype="application/json"):
        data = body.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        u = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(u.query).items()}
        if u.path.endswith("xmltv.php"):
            return self._send('<?xml version="1.0" encoding="UTF-8"?><tv></tv>', "application/xml")
        if not u.path.endswith("player_api.php"):
            self.send_response(404)
            self.end_headers()
            return
        a, cat = q.get("action"), q.get("category_id")
        if a is None:
            body = account()
        elif a == "get_live_categories":
            body = [{"category_id": c, "category_name": n, "parent_id": 0} for c, n in LIVE_CATS]
        elif a == "get_live_streams":
            body = live_streams(cat)
        elif a == "get_vod_categories":
            body = [{"category_id": c, "category_name": n, "parent_id": 0} for c, n in VOD_CATS]
        elif a == "get_vod_streams":
            body = vod_streams(cat)
        elif a == "get_series_categories":
            body = [{"category_id": c, "category_name": n, "parent_id": 0} for c, n in SERIES_CATS]
        elif a == "get_series":
            body = series(cat)
        elif a == "get_short_epg":
            body = {"epg_listings": []}
        else:
            body = []
        self._send(json.dumps(body, ensure_ascii=False))

    def log_message(self, fmt, *args):
        sys.stderr.write("mock_xtream " + (fmt % args) + "\n")


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    ThreadingHTTPServer(("0.0.0.0", port), Handler).serve_forever()
