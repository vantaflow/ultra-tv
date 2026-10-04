#!/bin/bash
L=$1; PK=com.ultratv.tv.nativeapp.debug; S=$(cd $(dirname $0); pwd); mkdir -p $S/raw
A="adb shell am start -n $PK/com.ultratv.tv.nativeapp.MainActivity"
snap(){ adb exec-out screencap -p > $S/raw/$L-$1.png; }
go(){ name=$1; shift; $A "$@" >/dev/null 2>&1; sleep ${WAIT:-4}; snap $name; }
key(){ for k in "$@"; do adb shell input keyevent $k; sleep 0.4; done; }
go home --es debug_route home --es debug_theme dark
go home-light --es debug_route home --es debug_theme light
go live --es debug_route live --es debug_theme dark
go guide --es debug_route guide; sleep 3; snap guide
go detail --es debug_route movies/1
go settings --es debug_route settings --ei debug_rub 0
go home2 --es debug_route home
if [ -n "$KEYNAV" ]; then
WAIT=7 go settings2 --es debug_route settings
for i in 1 2 3 4 5 6; do key KEYCODE_DPAD_DOWN; done; sleep 1; snap languages
key KEYCODE_DPAD_DOWN; sleep 1.5; snap profilespane
else
WAIT=7 go languages --es debug_route settings --ei debug_rub 6
go home2 --es debug_route home
WAIT=7 go profilespane --es debug_route settings --ei debug_rub 7
fi
go live --es debug_route live
key KEYCODE_DPAD_RIGHT KEYCODE_DPAD_CENTER; sleep 9; key KEYCODE_DPAD_CENTER; sleep 1; snap player
go home2 --es debug_route home
if [ "$L" = ar ]; then TOR=KEYCODE_DPAD_RIGHT; else TOR=KEYCODE_DPAD_LEFT; fi
key $TOR $TOR $TOR; for i in $(seq 1 12); do key KEYCODE_DPAD_DOWN; done; key KEYCODE_DPAD_CENTER; sleep 2; snap profiles
