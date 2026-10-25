#!/usr/bin/env bash
# CineTV — Nuvio, set up for the viewer: a few questions, then the addons are installed on the TV
# through Nuvio's own install links (no typing on the remote) and the answers are written to
# nuvio-preferences.json, which an AI agent (see AGENTS.md) uses to finish Nuvio's settings screens.
#
#   scripts/setup_nuvio.sh                 # asks
#   scripts/setup_nuvio.sh --defaults tr   # Turkish defaults, no questions
#
# Installed addons (each can be removed in Nuvio › Addons):
#   Torrentio       streams, configured from the answers (languages, quality, excluded qualities)
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

# Torrentio: languages first, highest quality first, excluded qualities filtered out.
LANGS="$CONTENT_LANG"
[ "$CONTENT_LANG" = "english" ] || LANGS="$CONTENT_LANG,english"
FILTER="$NEVER_QUAL"
case "$QUALITY" in 1080p) FILTER="$FILTER,4k,brremux" ;; 720p) FILTER="$FILTER,4k,brremux,1080p" ;; esac
TORRENTIO="torrentio.strem.fun/sort=qualitysize|language=$LANGS|qualityfilter=$FILTER/manifest.json"

install_addon() { # install_addon NAME HOST/PATH/manifest.json
    # Single quotes for the TV's shell: Torrentio options contain "|".
    adb shell "am start -a android.intent.action.VIEW -d 'stremio://$2' com.nuvio.tv" >/dev/null 2>&1
    echo "  ✓ $1"
    sleep 4
}

echo "Installing addons in Nuvio"
install_addon "Torrentio ($LANGS · up to $QUALITY)" "$TORRENTIO"
install_addon "TR Altyazı" "tr-altyazi.altyazi.workers.dev/manifest.json"
for name in AIOMetadata Xperience "Kaptain's Collection"; do
    URL=""
    if [ -z "$DEFAULTS" ] && [ -t 0 ]; then
        case "$name" in AIOMetadata) hint=" (create it at https://aiometadata.elfhosted.com/configure)" ;; *) hint="" ;; esac
        read -r -p "  Paste the $name manifest URL$hint, or Enter to skip: " URL
    fi
    if [ -n "$URL" ]; then install_addon "$name" "${URL#*://}"; else echo "  - $name skipped"; fi
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
