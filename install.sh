#!/usr/bin/env bash
# =================================================================================================
# CineTV installer — turns an Android TV / Google TV into CineTV over adb, in one command.
#
#   ./install.sh                         # interactive: finds the TV, asks a few questions
#   ./install.sh --tv 192.168.1.50 --yes # unattended, latest release from GitHub
#   ./install.sh --verify                # only checks an existing installation
#
# Options
#   --tv HOST[:PORT]      TV address (Wi-Fi adb). Omit to use the only connected device.
#   --apk FILE            Install this APK instead of downloading the latest release.
#   --version vX.Y.Z      Download this release instead of the latest.
#   --city NAME           City for the weather next to the clock.
#   --theme ID            cinetv (Alptug Default) | glass | aurora | scenes | dussehra (Royal Gold) | six-columns (Cinema Wall) | oled
#   --debloat             Disable the TV's bloatware (reversible, never touches TV core).
#   --youtube             Install ad-free YouTube (TizenTube) and retire the stock app (reversible).
#   --apps                Also install Nuvio, Nova Video Player and the AirPlay receiver (official releases).
#   --nuvio               Set Nuvio up: a few questions, then its addons are installed (scripts/setup_nuvio.sh).
#   --keep-old-launcher   Leave the stock home screen enabled (CineTV is still set as Home).
#   --yes                 Never ask; use defaults for anything not given.
#   --verify              Check the installation and print PASS / FAIL lines, change nothing.
#
# API keys are optional and never go on the command line (shell history). Provide them as
# environment variables, or type them when asked (input is hidden):
#   CINETV_TMDB_KEY=...  CINETV_TMDB_TOKEN=...  CINETV_MDBLIST_KEY=...  CINETV_AI_KEY=...  ./install.sh
# They are handed to the TV once and the launcher moves them into its encrypted keystore.
# =================================================================================================
set -euo pipefail

REPO="AlptugDag/cinetv-launcher"
PKG="com.cinetv.launcher"
# Package id of CineTV builds before 2.0; replaced on install so two Homes never compete.
LEGACY_PKG="com.gathin.tvhome"
HOME_ACTIVITY="$PKG/.MainActivity"
KEY_SERVICE="$PKG/$PKG.TvKeyInterceptorService"
DREAM="$PKG/$PKG.ClockDream"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

TV="" APK="" VERSION="" CITY="" THEME="" DEBLOAT=0 YOUTUBE=0 APPS=0 NUVIO=0 KEEP_OLD=0 YES=0 VERIFY_ONLY=0

bold() { printf '\033[1m%s\033[0m\n' "$*"; }
ok()   { printf '  \033[32m✓\033[0m %s\n' "$*"; }
warn() { printf '  \033[33m!\033[0m %s\n' "$*"; }
fail() { printf '  \033[31m✗\033[0m %s\n' "$*"; }
die()  { fail "$*"; exit 1; }
ask()  { # ask "question" default -> echo answer
    if [ "$YES" = 1 ] || [ ! -t 0 ]; then echo "$2"; return; fi
    local a; read -r -p "  $1 [$2]: " a; echo "${a:-$2}"
}

while [ $# -gt 0 ]; do
    case "$1" in
        --tv) TV="$2"; shift 2 ;;
        --apk) APK="$2"; shift 2 ;;
        --version) VERSION="$2"; shift 2 ;;
        --city) CITY="$2"; shift 2 ;;
        --theme) THEME="$2"; shift 2 ;;
        --debloat) DEBLOAT=1; shift ;;
        --youtube) YOUTUBE=1; shift ;;
        --apps) APPS=1; shift ;;
        --nuvio) NUVIO=1; shift ;;
        --keep-old-launcher) KEEP_OLD=1; shift ;;
        --yes|-y) YES=1; shift ;;
        --verify) VERIFY_ONLY=1; shift ;;
        -h|--help) sed -n '2,27p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) die "Unknown option: $1 (see --help)" ;;
    esac
done

# --- 1. adb and the TV --------------------------------------------------------------------------
command -v adb >/dev/null 2>&1 || die "adb not found. macOS: brew install android-platform-tools · Debian/Ubuntu: sudo apt install adb · Windows: use WSL or install Platform Tools."

bold "CineTV installer"
if [ -n "$TV" ]; then
    case "$TV" in *:*) ;; *) TV="$TV:5555" ;; esac
    adb connect "$TV" >/dev/null 2>&1 || true
    export ANDROID_SERIAL="$TV"
else
    DEVICES=$(adb devices | awk 'NR>1 && $2=="device" {print $1}')
    COUNT=$(printf '%s\n' "$DEVICES" | grep -c . || true)
    if [ "$COUNT" = 0 ]; then
        TV=$(ask "TV IP address (Settings › Network on the TV)" "")
        [ -n "$TV" ] || die "No TV given. Enable Developer options › USB/network debugging on the TV and pass --tv <ip>."
        case "$TV" in *:*) ;; *) TV="$TV:5555" ;; esac
        adb connect "$TV" >/dev/null 2>&1 || true
        export ANDROID_SERIAL="$TV"
    elif [ "$COUNT" -gt 1 ]; then
        die "Several adb devices are connected; choose one with --tv. ($(echo $DEVICES))"
    else
        export ANDROID_SERIAL="$DEVICES"
    fi
fi

for i in 1 2 3 4 5 6 7 8 9 10; do
    STATE=$(adb get-state 2>/dev/null || true)
    [ "$STATE" = "device" ] && break
    [ $i = 1 ] && warn "Waiting for the TV. If it shows \"Allow USB debugging?\", choose Always allow."
    sleep 3
done
[ "$(adb get-state 2>/dev/null || true)" = "device" ] || die "Cannot reach the TV over adb ($ANDROID_SERIAL)."

tv() { adb shell "$@" 2>/dev/null | tr -d '\r'; }
MODEL="$(tv getprop ro.product.manufacturer) $(tv getprop ro.product.model)"
SDK=$(tv getprop ro.build.version.sdk)
RAM_MB=$(( $(tv "grep MemTotal /proc/meminfo" | awk '{print $2}') / 1024 ))
ok "Connected: $MODEL · Android API $SDK · ${RAM_MB} MB RAM"
[ "${SDK:-0}" -ge 26 ] || die "CineTV needs Android 8.0 (API 26) or newer."

# --- verification (also the last step of an install) --------------------------------------------
verify() {
    local failed=0
    bold "Checking the installation"
    if tv pm list packages | grep -qx "package:$PKG"; then
        ok "CineTV installed ($(tv dumpsys package $PKG | grep -m1 versionName | sed 's/.*=//'))"
    else fail "CineTV is not installed"; return 1; fi
    if tv dumpsys package $PKG | grep -q "WRITE_SECURE_SETTINGS: granted=true"; then ok "Settings permission granted"
    else fail "WRITE_SECURE_SETTINGS not granted"; failed=1; fi
    if tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | grep -q "$PKG"; then ok "CineTV is the Home app"
    else fail "CineTV is not the Home app"; failed=1; fi
    if tv settings get secure enabled_accessibility_services | grep -q "$KEY_SERVICE"; then ok "Remote button service enabled"
    else warn "Remote button service not enabled (dedicated remote buttons only work on the home screen)"; fi
    if tv settings get secure screensaver_components | grep -q "$DREAM"; then ok "Film-moments screensaver selected"
    else warn "Screensaver not selected"; fi
    local ip
    ip=$(tv "ip -4 addr show wlan0 2>/dev/null; ip -4 addr show eth0 2>/dev/null" | awk '/inet /{print $2}' | cut -d/ -f1 | head -1)
    if [ -n "$ip" ] && command -v curl >/dev/null 2>&1 && curl -s -m 4 -o /dev/null "http://$ip:8080/"; then
        ok "Phone companion reachable at http://$ip:8080 (open it on a phone and allow it on the TV)"
        if curl -s -m 6 -o /dev/null "https://$(echo "$ip" | tr . -).local-ip.sh:9443/api/ping"; then
            ok "Secure phone page at https://$(echo "$ip" | tr . -).local-ip.sh:9443 (no \"not secure\" warning)"
        fi
    else warn "Phone companion not reachable from this computer yet (it starts with the home screen)"; fi
    return $failed
}

if [ "$VERIFY_ONLY" = 1 ]; then verify; exit $?; fi

# --- 2. the APK -----------------------------------------------------------------------------------
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
if [ -z "$APK" ]; then
    command -v curl >/dev/null 2>&1 || die "curl is needed to download the release (or pass --apk)."
    API="https://api.github.com/repos/$REPO/releases/${VERSION:+tags/$VERSION}"
    [ -n "$VERSION" ] || API="${API}latest"
    bold "Downloading CineTV ${VERSION:-(latest release)}"
    JSON=$(curl -fsSL "$API") || die "Could not reach GitHub releases. Use --apk with a local build."
    URL=$(printf '%s' "$JSON" | grep -o '"browser_download_url": *"[^"]*\.apk"' | head -1 | sed 's/.*"\(http[^"]*\)"/\1/')
    SUMS=$(printf '%s' "$JSON" | grep -o '"browser_download_url": *"[^"]*SHA256SUMS[^"]*"' | head -1 | sed 's/.*"\(http[^"]*\)"/\1/')
    [ -n "$URL" ] || die "No APK found in the release."
    APK="$TMP/$(basename "$URL")"
    curl -fsSL -o "$APK" "$URL"
    if [ -n "$SUMS" ]; then
        curl -fsSL -o "$TMP/SHA256SUMS" "$SUMS"
        EXPECTED=$(grep "$(basename "$APK")" "$TMP/SHA256SUMS" | awk '{print $1}')
        ACTUAL=$( (sha256sum "$APK" 2>/dev/null || shasum -a 256 "$APK") | awk '{print $1}')
        [ "$EXPECTED" = "$ACTUAL" ] || die "Checksum mismatch: the download is not the published release."
        ok "Checksum verified"
    fi
fi
[ -f "$APK" ] || die "APK not found: $APK"

# --- 3. questions ----------------------------------------------------------------------------------
[ -n "$CITY" ] || CITY=$(ask "City for the weather (empty = automatic)" "")
[ -n "$THEME" ] || THEME=$(ask "Theme: cinetv, glass, aurora, scenes, dussehra, six-columns, oled" "cinetv")
if [ "$YOUTUBE" = 0 ] && [ "$(ask "Install ad-free YouTube (TizenTube)? y/n" "y")" = "y" ] && [ "$YES" = 0 ]; then YOUTUBE=1; fi
if [ "$APPS" = 0 ] && [ "$YES" = 0 ] && [ "$(ask "Install Nuvio, Nova Video Player and the AirPlay receiver? y/n" "y")" = "y" ]; then APPS=1; fi
if [ "$NUVIO" = 0 ] && [ "$YES" = 0 ] && [ "$(ask "Set Nuvio up now (languages, quality, addons)? y/n" "y")" = "y" ]; then NUVIO=1; fi
if [ "$DEBLOAT" = 0 ] && [ "$YES" = 0 ] && [ "$(ask "Disable bloatware (reversible)? y/n" "n")" = "y" ]; then DEBLOAT=1; fi
AI_KEY="${CINETV_AI_KEY:-}"; TMDB_KEY="${CINETV_TMDB_KEY:-}"; TMDB_TOKEN="${CINETV_TMDB_TOKEN:-}"; MDBLIST_KEY="${CINETV_MDBLIST_KEY:-}"
if [ "$YES" = 0 ] && [ -t 0 ]; then
    [ -n "$TMDB_KEY" ] || { read -r -s -p "  TMDB API key (optional, localized titles & posters; Enter to skip): " TMDB_KEY; echo; }
    [ -n "$AI_KEY" ] || { read -r -s -p "  Gemini or Claude API key (optional, AI search & curation; Enter to skip): " AI_KEY; echo; }
fi

# --- 4. install -------------------------------------------------------------------------------------
bold "Installing"
if tv pm list packages | grep -qx "package:$LEGACY_PKG"; then
    warn "An older CineTV build ($LEGACY_PKG) is installed; replacing it."
    adb uninstall "$LEGACY_PKG" >/dev/null 2>&1 || true
fi
if ! adb install -r "$APK" >/dev/null 2>"$TMP/err"; then
    if grep -q "INSTALL_FAILED_UPDATE_INCOMPATIBLE" "$TMP/err"; then
        warn "The installed CineTV was signed with a different key; reinstalling."
        adb uninstall "$PKG" >/dev/null 2>&1 || true
        adb install "$APK" >/dev/null || die "Install failed: $(cat "$TMP/err")"
    else die "Install failed: $(cat "$TMP/err")"; fi
fi
ok "App installed"

tv pm grant $PKG android.permission.WRITE_SECURE_SETTINGS && ok "Settings permission granted" || warn "Could not grant WRITE_SECURE_SETTINGS"
tv appops set $PKG SYSTEM_ALERT_WINDOW allow >/dev/null || true
# AirPods and other paired headphones reconnect by themselves (Android 12+ asks for this at runtime).
tv pm grant $PKG android.permission.BLUETOOTH_CONNECT >/dev/null 2>&1 || true
tv cmd package set-home-activity "$HOME_ACTIVITY" >/dev/null && ok "Set as Home" || warn "Choose CineTV when the TV asks for a Home app"

CURRENT=$(tv settings get secure enabled_accessibility_services)
case "$CURRENT" in *"$KEY_SERVICE"*) ;; null|"") tv settings put secure enabled_accessibility_services "$KEY_SERVICE" ;;
    *) tv settings put secure enabled_accessibility_services "$CURRENT:$KEY_SERVICE" ;; esac
tv settings put secure accessibility_enabled 1
tv settings put secure screensaver_enabled 1
tv settings put secure screensaver_components "$DREAM"
ok "Remote buttons and screensaver configured"

[ -z "$CITY" ] || { tv settings put global cinetv_city "\"$CITY\""; ok "Weather city: $CITY"; }
[ -z "$TMDB_KEY" ] || { tv settings put secure tv_tmdb_api_key "$TMDB_KEY"; ok "TMDB key handed over (moved into the encrypted store on first start)"; }
[ -z "$TMDB_TOKEN" ] || { tv settings put secure tv_tmdb_read_token "$TMDB_TOKEN"; ok "TMDB read token handed over"; }
[ -z "$MDBLIST_KEY" ] || { tv settings put secure tv_mdblist_api_key "$MDBLIST_KEY"; ok "MDBList key handed over"; }
[ -z "$AI_KEY" ] || { tv settings put secure tv_ai_api_key "$AI_KEY"; ok "AI key handed over (moved into the encrypted store on first start)"; }

if [ "$KEEP_OLD" = 0 ]; then
    for stock in com.google.android.tvlauncher com.google.android.apps.tv.launcherx; do
        if tv pm list packages -e | grep -qx "package:$stock"; then
            tv pm disable-user --user 0 "$stock" >/dev/null && ok "Stock home screen disabled ($stock, reversible)"
        fi
    done
fi

if [ "$DEBLOAT" = 1 ]; then
    BRAND=$(tv getprop ro.product.brand | tr '[:upper:]' '[:lower:]')
    case "$BRAND" in arcelik|beko|grundig) bash "$DIR/scripts/debloat_arcelik_beko.sh" ;; *) bash "$DIR/scripts/debloat_universal.sh" ;; esac
fi

if [ "$APPS" = 1 ]; then bash "$DIR/scripts/setup_apps.sh"
elif [ "$YOUTUBE" = 1 ]; then bash "$DIR/scripts/setup_youtube.sh"; fi
if [ "$NUVIO" = 1 ]; then
    if [ "$YES" = 1 ]; then bash "$DIR/scripts/setup_nuvio.sh" --defaults tr; else bash "$DIR/scripts/setup_nuvio.sh"; fi
fi

# --- 5. start ---------------------------------------------------------------------------------------
# Restart so the launcher picks up the handed-over keys (it moves them into its keystore on start).
tv am force-stop $PKG
tv am start -n "$HOME_ACTIVITY" ${THEME:+--es theme "$THEME"} >/dev/null
ok "On first start CineTV tunes picture & sound, switches on ad-blocking DNS and measures the network (Settings › Picture, sound & network tuning to repeat)."
if [ -n "$THEME" ] && [ "$THEME" != "cinetv" ]; then
    warn "Pick the \"$THEME\" theme in CineTV › Settings › Themes (themes are chosen on the TV)."
fi
sleep 4
verify || true
bold "Done. Press Home on the remote. Open Settings › Connect phone to pair your phone."
