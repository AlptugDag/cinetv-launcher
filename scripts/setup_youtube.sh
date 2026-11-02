#!/usr/bin/env bash
# CineTV — ad-free YouTube.
#
# Installs TizenTube Cobalt (ad-free YouTube TV client; SmartTube as fallback) and disables the
# stock YouTube app (reversible: Settings › Apps › YouTube › Enable). CineTV then shows it with the
# YouTube name and logo, sends the remote's YouTube button and every YouTube link to it, and
# redirects anything that still opens the stock app.
set -euo pipefail

adb get-state >/dev/null 2>&1 || { echo "No adb device. Run: adb connect <TV_IP>:5555" >&2; exit 1; }
tv() { adb shell "$@" 2>/dev/null | tr -d '\r'; }

for pkg in io.gh.reisxd.tizentube.cobalt com.teamsmart.videomanager.tv com.liskovsoft.smarttubetv.beta; do
    if tv pm list packages | grep -qx "package:$pkg"; then
        echo "Ad-free YouTube already installed: $pkg"
        INSTALLED="$pkg"
        break
    fi
done

if [ -z "${INSTALLED:-}" ]; then
    TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
    ABI="$(tv getprop ro.product.cpu.abi)"
    case "$ABI" in arm64*) ASSET="cobalt-arm64.apk" ;; *) ASSET="cobalt-arm.apk" ;; esac
    URL="$(curl -fsSL https://api.github.com/repos/reisxd/TizenTubeCobalt/releases/latest \
        | grep -o "\"browser_download_url\": *\"[^\"]*$ASSET\"" | sed 's/.*"\(http[^"]*\)"/\1/' || true)"
    if [ -n "$URL" ] && curl -fsSL -o "$TMP/yt.apk" "$URL" && adb install -r "$TMP/yt.apk" >/dev/null; then
        INSTALLED="io.gh.reisxd.tizentube.cobalt"
    else
        echo "TizenTube download failed; installing SmartTube instead."
        curl -fsSL -o "$TMP/yt.apk" "https://github.com/yuliskov/SmartTube/releases/latest/download/smarttube_stable.apk"
        adb install -r "$TMP/yt.apk" >/dev/null
        INSTALLED="com.teamsmart.videomanager.tv"
    fi
    echo "Installed $INSTALLED"
fi

if tv pm list packages -e | grep -qx "package:com.google.android.youtube.tv"; then
    tv pm disable-user --user 0 com.google.android.youtube.tv >/dev/null && echo "Stock YouTube disabled (reversible)"
fi
echo "Done: the YouTube tile, the remote's YouTube button and YouTube links now open $INSTALLED."
