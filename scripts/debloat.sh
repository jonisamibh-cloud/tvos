#!/usr/bin/env bash
# Usage:
#   ./scripts/debloat.sh TV_IP:5555 list            -> saves third-party and system package lists
#   ./scripts/debloat.sh TV_IP:5555 remove file.txt -> disables packages listed in file (one per line)
#   ./scripts/debloat.sh TV_IP:5555 restore file.txt
set -e
TV="$1"; CMD="$2"; FILE="$3"
case "$CMD" in
  list)
    adb -s "$TV" shell pm list packages -3 | sed 's/package://' | tr -d '\r' > third_party.txt
    adb -s "$TV" shell pm list packages -s | sed 's/package://' | tr -d '\r' > system.txt
    echo "Saved third_party.txt and system.txt";;
  remove)
    while read -r p; do [ -n "$p" ] && adb -s "$TV" shell pm uninstall -k --user 0 "$p"; done < "$FILE";;
  restore)
    while read -r p; do [ -n "$p" ] && adb -s "$TV" shell cmd package install-existing "$p"; done < "$FILE";;
  *) echo "Use list, remove or restore";;
esac
