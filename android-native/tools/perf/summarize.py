#!/usr/bin/env python3
"""Tableau comparatif des résultats de measure.py : python3 summarize.py a.json b.json c.json

Par scénario et par version : images rendues (somme des passes), part des images > 16 ms et > 32 ms, médiane des percentiles
50/90/95/99 (ms) sur les passes, mémoire PSS médiane (Mo) à la fin, temps de démarrage médian (ms, `am start -W`).
"""
import json, statistics, sys


def med(xs):
    xs = [x for x in xs if x is not None]
    return statistics.median(xs) if xs else None


rows = []
data = [json.load(open(p)) for p in sys.argv[1:]]
names = []
for d in data:
    for n in d["scenarios"]:
        if n not in names:
            names.append(n)
print(f"{'scénario':14} {'version':10} {'images':>7} {'>16ms':>7} {'>32ms':>7} {'p50':>5} {'p90':>5} {'p95':>5} {'p99':>5} {'PSS Mo':>7} {'start ms':>8}")
for n in names:
    for d in data:
        runs = d["scenarios"].get(n)
        if not runs:
            continue
        fr = sum(r["frames"] for r in runs) or 1
        o16 = 100.0 * sum(r["over16"] for r in runs) / fr
        o32 = 100.0 * sum(r["over32"] for r in runs) / fr
        pss = med([r.get("pss_after_mb") or r.get("pss_mb") for r in runs])
        st = med([r.get("total_ms") for r in runs])
        print(f"{n:14} {d['label']:10} {fr:7d} {o16:6.1f}% {o32:6.1f}% {med([r['p50'] for r in runs]):5.0f} {med([r['p90'] for r in runs]):5.0f} "
              f"{med([r['p95'] for r in runs]):5.0f} {med([r['p99'] for r in runs]):5.0f} {pss if pss is not None else '-':>7} {st if st is not None else '-':>8}")
    print()
