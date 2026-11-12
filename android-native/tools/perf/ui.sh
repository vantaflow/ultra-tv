# Fonctions communes (à sourcer sur la machine de test) : SERIAL = émulateur visé (défaut emulator-5556), SHOTS = dossier des captures.
A=~/android-sdk/platform-tools/adb
S="$A -s ${SERIAL:-emulator-5556}"
SHOTS=${SHOTS:-/tmp/utv.GRy0/perf-run/shots}
mkdir -p "$SHOTS"
key() { for k in "$@"; do $S shell input keyevent $k; sleep ${KEYWAIT:-0.5}; done; }
shot() { $S exec-out screencap -p > "$SHOTS/$1.png"; }
