#!/usr/bin/env bash
# Captures d'écran de l'app Android TV sur émulateur (CI, workflow « UI screenshots »).
# Prérequis : émulateur démarré (adb), APK debug construit, faux serveur Xtream sur le port 8080 de l'hôte.
set -u
OUT=${OUT:-screens}
mkdir -p "$OUT"
PKG=com.ultratv.tv.nativeapp.debug
ACT="$PKG/com.ultratv.tv.nativeapp.MainActivity"

APK=$(ls android-native/app/build/outputs/apk/debug/*.apk | head -1)
echo "APK: $APK"
adb install -r -g "$APK"

shot() { sleep "${2:-3}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "capture $1"; }
key() { for k in "$@"; do adb shell input keyevent "$k"; sleep 0.6; done; }
# Une seule chaîne passée au shell de l'émulateur : les valeurs contenant « | » ou des espaces y sont entre apostrophes.
start() { adb shell "am start -n $ACT $*" >/dev/null; }

# 1. Premier lancement + source de démo, synchro
start --es debug_lang fr --es debug_xtream "'http://10.0.2.2:8080|demo|demo'"
shot 00-first-launch 20
# Laisse la synchro finir (catalogue fictif, petit), puis relance : l'accueil remplace l'assistant de démarrage.
sleep 45
adb shell am force-stop "$PKG"
start --es debug_route home
shot 01-home 15

# 2. Menu latéral : focus vers la gauche jusqu'au rail (ouvert)
key DPAD_LEFT DPAD_LEFT DPAD_LEFT DPAD_LEFT
shot 02-menu-open 2
key DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN
shot 03-menu-open-focus 2
key DPAD_RIGHT
shot 04-menu-closed 2

# 3. Recherche : résultats « nuit » (plus de 12), onglet Films, tri A–Z
start --es debug_route search --es debug_query nuit
shot 10-search-all 8
key DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT
shot 11-search-focus 2
start --es debug_route search --es debug_query "'nuitt blance'"
shot 12-search-fuzzy 8
start --es debug_route search --es debug_query "''"
shot 13-search-start 6

# 4. Films (catalogue)
start --es debug_route movies
shot 20-movies 8

adb logcat -d > "$OUT/logcat-full.txt" 2>/dev/null || true
grep -iE "ultra|xtream|sync|FATAL|AndroidRuntime: (FATAL|java)" "$OUT/logcat-full.txt" | tail -400 > "$OUT/logcat.txt" || true
rm -f "$OUT/logcat-full.txt"
ls -la "$OUT"
