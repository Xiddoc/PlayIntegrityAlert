#!/usr/bin/env bash
# Play Integrity Alert — LSPosed module-hook e2e driver.
#
# Adapted from Beetroot's tests/fixtures/.../lsposed-hook-e2e.sh. Proves the
# module loads under LSPosed inside an app it is scoped to: installs the module
# APK, enables it in LSPosed's scope for a target package, and (after a reboot)
# asserts the module's PIA_MODULE_LOADED marker appears in LSPosed's module log
# when the target launches. (Detection itself runs in the Play Store process and
# needs GApps + a real integrity caller, so this gate proves module load.)
#
# Prerequisites: the instance is booted with the Vector/LSPosed framework active
# (a Zygisk module goes live on the *second* boot, so the caller flashes Vector
# and reboots once before `setup`).
#
# Usage — two phases around a reboot the caller performs:
#   e2e-lsposed.sh setup <adb-serial> <module.apk> <target-pkg>
#   # ... caller reboots the instance (e.g. `beetroot restart <name>`) ...
#   e2e-lsposed.sh check <adb-serial> <target-pkg>
set -euo pipefail

_sh() { adb -s "$SERIAL" shell "$1"; }

MODULE_PKG="com.xiddoc.playintegrityalert"
MARKER="PIA_MODULE_LOADED"
LSPD_LOG='/data/adb/lspd/log/modules_*.log'

cmd_setup() {
    local apk="$1" target="$2"
    echo "[*] installing module: $apk"
    adb -s "$SERIAL" install -r "$apk"
    echo "[*] enabling '$MODULE_PKG' in LSPosed scope for '$target' (and itself, for status)"
    _sh "DB=/data/adb/lspd/config/modules_config.db; \
         APK=\$(pm path $MODULE_PKG | sed 's/package://'); \
         sqlite3 \"\$DB\" \"INSERT OR REPLACE INTO modules(module_pkg_name, apk_path, enabled, auto_include) VALUES('$MODULE_PKG', '\$APK', 1, 0);\"; \
         for p in '$target' '$MODULE_PKG'; do \
           sqlite3 \"\$DB\" \"INSERT OR IGNORE INTO scope(mid, app_pkg_name, user_id) SELECT mid, '\$p', 0 FROM modules WHERE module_pkg_name='$MODULE_PKG';\"; \
         done; \
         echo 'enabled modules:'; sqlite3 \"\$DB\" 'SELECT module_pkg_name FROM modules WHERE enabled=1;'; \
         echo 'scope:'; sqlite3 \"\$DB\" 'SELECT app_pkg_name FROM scope;'"
    echo "[*] setup done — reboot the instance, then run: $0 check $SERIAL $target"
}

cmd_check() {
    local target="$1"
    echo "[*] launching $target to load the module into it"
    _sh "am force-stop $target" || true
    _sh "monkey -p $target -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || am start -n $target/.Settings >/dev/null 2>&1 || true"
    local deadline=$(($(date +%s) + 120))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        if _sh "cat $LSPD_LOG 2>/dev/null" | grep -q "$MARKER pkg=$target"; then
            echo "[+] PASS — module loaded into target:"
            _sh "cat $LSPD_LOG 2>/dev/null" | grep "PIA_" | tail -10
            return 0
        fi
        sleep 5
    done
    echo "[!] FAIL — $MARKER not found for $target. Module log tail:"
    _sh "cat $LSPD_LOG 2>/dev/null" | grep -i "PIA_\|playintegrityalert" | tail -10 || true
    return 1
}

main() {
    local sub="${1:?usage: $0 setup|check <serial> ...}"
    SERIAL="${2:?adb serial required (e.g. 127.0.0.1:5555)}"
    adb connect "$SERIAL" >/dev/null 2>&1 || true
    adb -s "$SERIAL" root >/dev/null 2>&1 || true
    sleep 2
    adb connect "$SERIAL" >/dev/null 2>&1 || true
    case "$sub" in
    setup) cmd_setup "${3:?module apk}" "${4:?target pkg}" ;;
    check) cmd_check "${3:?target pkg}" ;;
    *) echo "unknown subcommand: $sub" >&2; exit 2 ;;
    esac
}

main "$@"
