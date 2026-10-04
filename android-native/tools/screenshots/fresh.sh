#!/bin/bash
# fresh.sh LANG : app vierge + source DEMO + langue
S=$(cd $(dirname $0); pwd); PK=com.ultratv.tv.nativeapp.debug
adb shell pm clear $PK >/dev/null
adb shell cmd locale set-app-locales $PK --locales en-US >/dev/null
adb shell am start -n $PK/com.ultratv.tv.nativeapp.MainActivity >/dev/null
for i in $(seq 1 30); do sleep 3; sz=$(adb shell run-as $PK stat -c %s databases/ultra-tv.db 2>/dev/null | tr -d '\r'); [ -n "$sz" ] && [ "$sz" -gt 4000 ] && break; done; sleep 3
python3 $S/inject_demo.py
adb shell am start -n $PK/com.ultratv.tv.nativeapp.MainActivity --es debug_lang $1 >/dev/null
sleep 25
