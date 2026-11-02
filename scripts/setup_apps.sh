#!/usr/bin/env bash
# CineTV — the companion apps, each from its official GitHub release, matched to the TV's CPU:
#
#   Nuvio          films & series (NuvioMedia/NuvioTV)
#   YouTube        ad-free TizenTube (scripts/setup_youtube.sh; stock YouTube is retired, reversible)
#   Nova           video player for casting from the phone and as Nuvio's external player
#   AirPlay        AirPlay 2 receiver, so iPhones and Macs can send video and audio to the TV
#
#   scripts/setup_apps.sh [--only nuvio,youtube,nova,airplay]
#
# Already-installed apps are left as they are. Needs adb connected to the TV and curl.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ONLY="nuvio,youtube,nova,airplay"
[ "${1:-}" = "--only" ] && ONLY="${2:-$ONLY}"

adb get-state >/dev/null 2>&1 || { echo "No adb device. Run: adb connect <TV_IP>:5555" >&2; exit 1; }
tv() { adb shell "$@" 2>/dev/null | tr -d '\r'; }
want() { case ",$ONLY," in *",$1,"*) return 0 ;; *) return 1 ;; esac; }
installed() { tv pm list packages | grep -qx "package:$1"; }

ABI="$(tv getprop ro.product.cpu.abi)"
case "$ABI" in arm64*) ARCH="arm64-v8a" ;; armeabi*) ARCH="armeabi-v7a" ;; x86_64) ARCH="x86_64" ;; x86) ARCH="x86" ;; *) ARCH="universal" ;; esac
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT

# gh_apk REPO PATTERN... -> download the first release asset matching a pattern (in order) and install it
gh_apk() {
    local repo="$1"; shift
    local json url pattern
    json="$(curl -fsSL "https://api.github.com/repos/$repo/releases/latest")" || { echo "  ! cannot reach $repo releases" >&2; return 1; }
    for pattern in "$@"; do
        url="$(printf '%s' "$json" | grep -o '"browser_download_url": *"[^"]*"' | sed 's/.*"\(http[^"]*\)"/\1/' | grep -E "$pattern" | head -1 || true)"
        [ -n "$url" ] && break
    done
    [ -n "$url" ] || { echo "  ! no matching APK in $repo" >&2; return 1; }
    curl -fsSL -o "$TMP/app.apk" "$url"
    adb install -r "$TMP/app.apk" >/dev/null
    echo "  ✓ $(basename "$url")"
}

echo "CineTV companion apps (CPU: $ABI)"
if want nuvio; then
    if installed com.nuvio.tv; then echo "  ✓ Nuvio already installed"
    else gh_apk NuvioMedia/NuvioTV "app-full-$ARCH-release\.apk$" "app-full-universal-release\.apk$"; fi
fi
if want youtube; then bash "$DIR/setup_youtube.sh"; fi
if want nova; then
    if installed org.courville.nova; then echo "  ✓ Nova already installed"
    else gh_apk nova-video-player/aos-AVP "$ARCH-release\.apk$" "universal-release\.apk$"; fi
fi
if want airplay; then
    if installed io.github.jqssun.airplay; then echo "  ✓ AirPlay receiver already installed"
    else gh_apk jqssun/android-airplay-server "app-release\.apk$" && tv monkey -p io.github.jqssun.airplay 1 >/dev/null 2>&1 || true; fi
fi
echo "Done. Chromecast is built into Android TV; AirPlay runs in the background once opened."
