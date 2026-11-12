#!/usr/bin/env python3
"""Mesures de fluidité d'Ultra TV sur un émulateur Android TV (adb), sans module Macrobenchmark.

  python3 measure.py SERIAL PACKAGE LABEL [RUNS] [SCENARIOS] > resultat.json

Chaque scénario repart d'un démarrage à froid (force-stop), attend que l'accueil se stabilise, remet à zéro `dumpsys gfxinfo`,
joue une séquence de touches de télécommande (`input keyevent`, DPAD) ou un lien profond, puis lit le cumul de gfxinfo :
images rendues, images > 16 ms et > 32 ms (histogramme), percentiles 50/90/95/99, et la mémoire (`dumpsys meminfo`).
Le démarrage à froid est mesuré avec `am start -W` (TotalTime). Comparer des versions à catalogue égal (même source).
Les valeurs absolues d'un émulateur (GPU logiciel) ne valent rien pour une vraie box ; seule la COMPARAISON avant/après compte.
"""
import json, os, re, statistics, subprocess, sys, time

serial, pk, label = sys.argv[1], sys.argv[2], sys.argv[3]
RUNS = int(sys.argv[4]) if len(sys.argv) > 4 else 3
ONLY = sys.argv[5].split(",") if len(sys.argv) > 5 else None
ADB = [os.path.expanduser(os.environ.get("ADB_BIN", "~/android-sdk/platform-tools/adb")), "-s", serial]
ACT = f"{pk}/com.ultratv.tv.nativeapp.MainActivity"
SETTLE = int(os.environ.get("SETTLE", "30"))


def sh(*a):
    return subprocess.run(ADB + ["shell"] + list(a), capture_output=True, text=True).stdout


def keys(seq, gap=0.45):
    """Envoie une suite de touches avec une pause (une seule connexion adb)."""
    script = "; ".join(f"input keyevent {k}; sleep {gap}" for k in seq)
    sh(script)


def repeat(k, n):
    return [k] * n


DOWN, UP, RIGHT, LEFT, OK, BACK = "KEYCODE_DPAD_DOWN", "KEYCODE_DPAD_UP", "KEYCODE_DPAD_RIGHT", "KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_CENTER", "KEYCODE_BACK"


def cold_start():
    sh("am", "force-stop", pk)
    time.sleep(2)
    out = sh("am", "start", "-W", "-n", ACT)
    m = re.search(r"TotalTime:\s*(\d+)", out)
    return int(m.group(1)) if m else None


def link(uri):
    sh("am", "start", "-a", "android.intent.action.VIEW", "-d", uri, pk)


def gfx():
    out = sh("dumpsys", "gfxinfo", pk)
    d = {}
    for name, pat in [("frames", r"Total frames rendered:\s*(\d+)"), ("janky", r"Janky frames:\s*(\d+)"),
                      ("p50", r"50th percentile:\s*(\d+)ms"), ("p90", r"90th percentile:\s*(\d+)ms"),
                      ("p95", r"95th percentile:\s*(\d+)ms"), ("p99", r"99th percentile:\s*(\d+)ms"),
                      ("slow_ui", r"Number Slow UI thread:\s*(\d+)"), ("slow_draw", r"Number Slow issue draw commands:\s*(\d+)")]:
        m = re.search(pat, out)
        d[name] = int(m.group(1)) if m else 0
    h = re.search(r"HISTOGRAM:\s*(.*)", out)
    over16 = over32 = 0
    if h:
        for ms, n in re.findall(r"(\d+)ms=(\d+)", h.group(1)):
            ms, n = int(ms), int(n)
            if ms > 16: over16 += n
            if ms > 32: over32 += n
    d["over16"], d["over32"] = over16, over32
    return d


def mem():
    out = sh("dumpsys", "meminfo", pk)
    m = re.search(r"TOTAL PSS:\s*(\d+)", out) or re.search(r"^\s*TOTAL\s+(\d+)", out, re.M)
    return int(m.group(1)) // 1024 if m else None


def reset():
    sh("dumpsys", "gfxinfo", pk, "reset")


def dismiss_announcement():
    """Alertes distantes éventuelles (adresse du site…) : OK/Retour au démarrage sans toucher au reste."""
    pass


SCEN = {}


def scenario(name):
    def deco(f):
        SCEN[name] = f
        return f
    return deco


@scenario("home_scroll")
def home_scroll():
    """Accueil : toutes les rangées vers le bas, puis retour vers le haut, puis défilement latéral dans la première rangée."""
    reset()
    keys(repeat(DOWN, 14))
    keys(repeat(RIGHT, 8))
    keys(repeat(LEFT, 8))
    keys(repeat(UP, 14))


@scenario("home_idle")
def home_idle():
    """Accueil immobile 30 s : coût de fond (rotation « À la une », horloge, chargements différés)."""
    reset()
    time.sleep(30)


@scenario("detail_movie")
def detail_movie():
    """Films : liste, première affiche, ouverture de la fiche, un peu de navigation, retour."""
    sh("am", "start", "-n", ACT, "--es", "debug_route", "movies")
    time.sleep(6)
    reset()
    keys([RIGHT, OK])
    time.sleep(6)
    keys(repeat(DOWN, 4))
    keys([BACK])


@scenario("detail_series")
def detail_series():
    sh("am", "start", "-n", ACT, "--es", "debug_route", "series")
    time.sleep(6)
    reset()
    keys([RIGHT, OK])
    time.sleep(6)
    keys(repeat(DOWN, 4))
    keys([BACK])


@scenario("guide")
def guide():
    reset()
    sh("am", "start", "-n", ACT, "--es", "debug_route", "guide")
    time.sleep(8)
    keys(repeat(DOWN, 12))
    keys(repeat(RIGHT, 6))
    keys(repeat(UP, 6))


@scenario("live_zap")
def live_zap():
    reset()
    sh("am", "start", "-n", ACT, "--es", "debug_route", "live")
    time.sleep(8)
    keys([RIGHT])
    keys(repeat(DOWN, 15), gap=0.6)
    keys(repeat(UP, 8), gap=0.6)


def run(name):
    f = SCEN[name]
    res = []
    for i in range(RUNS):
        start = cold_start() if name != "cold_start" else None
        time.sleep(SETTLE)
        pss0 = mem()
        f()
        time.sleep(2)
        g = gfx()
        g["pss_before_mb"], g["pss_after_mb"] = pss0, mem()
        res.append(g)
        print(f"  {name} #{i + 1}: {g}", file=sys.stderr)
    return res


out = {"label": label, "package": pk, "runs": RUNS, "scenarios": {}}
starts = []
if not ONLY or "cold_start" in ONLY:
    for i in range(RUNS):
        t = cold_start()
        time.sleep(SETTLE)
        g = gfx()
        g["total_ms"] = t
        g["pss_mb"] = mem()
        starts.append(g)
        print(f"  cold_start #{i + 1}: {g}", file=sys.stderr)
    out["scenarios"]["cold_start"] = starts
for n in SCEN:
    if ONLY and n not in ONLY:
        continue
    out["scenarios"][n] = run(n)
sh("am", "force-stop", pk)
print(json.dumps(out, ensure_ascii=False, indent=1))
