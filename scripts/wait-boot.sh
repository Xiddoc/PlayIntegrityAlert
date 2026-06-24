#!/usr/bin/env bash
# Wait until an adb-reachable Android instance finishes booting.
# Usage: wait-boot.sh <adb-serial> [timeout-seconds]
set -euo pipefail

SERIAL="${1:?adb serial required (e.g. 127.0.0.1:5555)}"
TIMEOUT="${2:-600}"

adb connect "$SERIAL" >/dev/null 2>&1 || true
adb -s "$SERIAL" wait-for-device

deadline=$(($(date +%s) + TIMEOUT))
while [ "$(date +%s)" -lt "$deadline" ]; do
    adb connect "$SERIAL" >/dev/null 2>&1 || true
    if [ "$(adb -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
        echo "[*] $SERIAL boot completed"
        adb -s "$SERIAL" root >/dev/null 2>&1 || true
        sleep 3
        exit 0
    fi
    sleep 5
done

echo "[!] $SERIAL did not finish booting within ${TIMEOUT}s" >&2
exit 1
