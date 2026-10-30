#!/usr/bin/env bash
# Usage: ./scripts/setup.sh 192.168.1.50:5555 path/to/app-debug.apk
set -e
TV="$1"; APK="${2:-app-debug.apk}"
SVC="com.vibe.tvos/com.vibe.tvos.CursorService"
adb connect "$TV"
adb -s "$TV" install -r "$APK"
CUR=$(adb -s "$TV" shell settings get secure enabled_accessibility_services | tr -d '\r')
if [ -z "$CUR" ] || [ "$CUR" = "null" ]; then NEW="$SVC"; else NEW="$CUR:$SVC"; fi
adb -s "$TV" shell settings put secure enabled_accessibility_services "$NEW"
adb -s "$TV" shell settings put secure accessibility_enabled 1
adb -s "$TV" shell cmd package set-home-activity com.vibe.tvos/.MainActivity || true
adb -s "$TV" shell input keyevent KEYCODE_HOME
echo "Done."
