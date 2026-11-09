#!/usr/bin/env bash
# CineTV — Nuvio, set up for the viewer: a few questions, then the addons are installed on the TV
# through Nuvio's own install links (no typing on the remote) and the answers are written to
# nuvio-preferences.json, which an AI agent (see AGENTS.md) uses to finish Nuvio's settings screens.
#
#   scripts/setup_nuvio.sh                 # asks
#   scripts/setup_nuvio.sh --defaults tr   # Turkish defaults, no questions
#
# Installed addons (each can be removed in Nuvio › Addons):
#   Player Addons   catalog & stream provider configured from user preferences (languages, quality)
#   TR Altyazı      Turkish subtitle pool (AltyaziTR, OpenSubtitles, YIFY)
#   AIOMetadata     posters, plots and cast in your language (personal URL from its configure page)
#   Xperience, Kaptain's Collection   when you paste their manifest URLs
# Plugin repositories, added in Nuvio › Settings › Plugins › Add repository (printed at the end):
#   https://raw.githubusercontent.com/falsisdev/anthology/main/manifest.json
#   https://raw.githubusercontent.com/Wiojelt/TurkSinema/main/repo.json
set -euo pipefail

adb get-state >/dev/null 2>&1 || { echo "No adb device. Run: adb connect <TV_IP>:5555" >&2; exit 1; }
tv() { adb shell "$@" 2>/dev/null | tr -d '\r'; }
tv pm list packages | grep -qx "package:com.nuvio.tv" || { echo "Nuvio is not installed (scripts/setup_apps.sh --only nuvio)." >&2; exit 1; }

DEFAULTS=""
[ "${1:-}" = "--defaults" ] && DEFAULTS="${2:-tr}"
ask() { # ask "question" default
    if [ -n "$DEFAULTS" ] || [ ! -t 0 ]; then echo "$2"; return; fi
    local a; read -r -p "  $1 [$2]: " a; echo "${a:-$2}"
}

echo "Nuvio preferences"
CONTENT_LANG=$(ask "Audio / content language (turkish, english, ...)" "turkish")
HOME_LANG=$(ask "Language of titles, posters and plots (tr, en, ...)" "tr")
SUB1=$(ask "Subtitle language" "tr")
SUB2=$(ask "Second subtitle language (empty for none)" "en")
AUTOPLAY=$(ask "Play the first good source automatically? y/n" "y")
QUALITY=$(ask "Best quality to aim for: 4k, 1080p, 720p" "4k")
NEVER_QUAL=$(ask "Never show (comma list: cam,scr,480p,3d)" "cam,scr,480p")
GENRES_YES=$(ask "Favourite genres (comma list, empty = any)" "")
GENRES_NO=$(ask "Genres to hide (comma list)" "")
COUNTRIES_YES=$(ask "Favourite countries (comma list, empty = any)" "")
COUNTRIES_NO=$(ask "Countries to hide (comma list)" "")

install_addon() { # install_addon NAME HOST/PATH/manifest.json
    adb shell "am start -a android.intent.action.VIEW -d 'stremio://$2' com.nuvio.tv" >/dev/null 2>&1
    echo "  ✓ $1"
    sleep 4
}

echo "Installing curated addons in Nuvio"
install_addon "Cinemeta" "v3-cinemeta.strem.io/manifest.json"
install_addon "TR Altyazı" "tr-altyazi.altyazi.workers.dev/manifest.json"
install_addon "Anthology" "falsisdev.github.io/anthology/stremio/manifest.json"
install_addon "Netflix Catalog" "7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/bmZ4LGRucCxhdHAsaGJtLGFtcCxtYmk6OjoxNzkyODA2MTk4NzgwOjA6MDo%3D/manifest.json"
install_addon "Xperience" "xperience-app.com/manifest/3d7c0742-30bc-4285-8a05-2b9327cad6b0/eyJhbGciOiJIUzI1NiJ9.eyJwaWQiOiIzZDdjMDc0Mi0zMGJjLTQyODUtOGEwNS0yYjkzMjdjYWQ2YjAiLCJraWQiOiI1OTg2ZGMxYS00MzNhLTQ0MTQtYmU3Zi1iMzQ0M2NhZWY0NjQiLCJzY29wZSI6Im1hbmlmZXN0Iiwic3ViIjoiZjI3NmYwYjEtM2EzZC00ZGU1LTgwZDctZTljOWE3ZWE4YTFhIiwiaWF0IjowfQ.3KVz5c33icFDay9jjTLOtqJEl5m8huYoz9xYHPAwBhE/manifest.json"

echo "Deploying Alptuğ'un Filmleri, Xperience and AIOMetadata configs to /sdcard/Download/"
ASSETS_DIR="$(dirname "$0")/assets"
for cfg in "alptugun-filmleri-template.json" "xperience-kategori-setup-profile.json" "aiometadata-config.json"; do
    if [ -f "$ASSETS_DIR/$cfg" ]; then
        adb push "$ASSETS_DIR/$cfg" "/sdcard/Download/$cfg" >/dev/null 2>&1 || true
        adb shell "am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Download/$cfg" >/dev/null 2>&1 || true
    fi
done

cat > nuvio-preferences.json <<EOF
{
  "content_language": "$CONTENT_LANG",
  "metadata_language": "$HOME_LANG",
  "subtitle_language": "$SUB1",
  "second_subtitle_language": "$SUB2",
  "autoplay_first_source": $( [ "$AUTOPLAY" = "y" ] && echo true || echo false ),
  "max_quality": "$QUALITY",
  "never_qualities": "$NEVER_QUAL",
  "favourite_genres": "$GENRES_YES",
  "hidden_genres": "$GENRES_NO",
  "favourite_countries": "$COUNTRIES_YES",
  "hidden_countries": "$COUNTRIES_NO",
  "scraper_cache_enabled": true,
  "scraper_cache": {
    "enabled": true,
    "ttl_seconds": 86400,
    "catalog_ttl_seconds": 43200,
    "max_entries": 1500,
    "disk_cache_mb": 64,
    "stale_while_revalidate": true,
    "cache_streams": true,
    "cache_metadata": true
  },
  "scraper_priorities": {
    "anthology": 100,
    "torrentio": 100,
    "storz": 95,
    "meteor": 90,
    "comet": 90,
    "mediafusion": 85,
    "liderfilmizle": 80,
    "sinewix": 80,
    "wiosinema": 45,
    "izlelan": 20
  },
  "source_timeout_overrides": {
    "default_ms": 5500,
    "fast_tier_ms": 6000,
    "wiosinema_ms": 3800,
    "izlelan_ms": 2200
  },
  "source_circuit_breaker": {
    "enabled": true,
    "failure_threshold": 2,
    "recovery_timeout_ms": 1800000,
    "dead_hosts": [
      "izlelan.com",
      "api.izlelan.com",
      "www.izlelan.com"
    ]
  },
  "scraper_concurrency": 8,
  "scraper_race_mode": true,
  "early_stream_return": true,
  "plugin_repositories": [
    "https://raw.githubusercontent.com/falsisdev/anthology/main/manifest.json",
    "https://raw.githubusercontent.com/Wiojelt/TurkSinema/main/repo.json"
  ]
}
EOF
echo "Saved nuvio-preferences.json"
cat <<EOF

Finish in Nuvio (or let an AI agent do it, AGENTS.md › Nuvio):
  Settings › Plugins › Add repository:
     https://raw.githubusercontent.com/falsisdev/anthology/main/manifest.json
     https://raw.githubusercontent.com/Wiojelt/TurkSinema/main/repo.json
  Settings › Playback: subtitles $SUB1 (second: ${SUB2:-none}), autoplay first source: $AUTOPLAY
  Settings › Appearance / Language: $HOME_LANG
EOF
