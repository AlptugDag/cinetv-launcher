#!/usr/bin/env bash
# The phone page on GitHub Pages (docs/remote): the same page the TV serves, with its paths made relative.
# Opened from the TV's QR code, it reaches the TV over a direct WebRTC link instead of the home network.
# Run before each release; GitHub Pages serves docs/ from main.
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SRC="$DIR/launcher/assets/remote"
OUT="$DIR/docs/remote"
rm -rf "$OUT"
mkdir -p "$OUT/static"
sed -e 's#"/static/#"static/#g' -e "s#'/static/#'static/#g" "$SRC/index.html" > "$OUT/index.html"
cp "$SRC/sw.js" "$OUT/sw.js"
sed -e 's#"/static/#"static/#g' -e 's#"start_url": "/"#"start_url": "./"#' "$SRC/manifest.webmanifest" > "$OUT/static/manifest.webmanifest"
for f in icon.png icon-192.png signature.png creator.jpg; do [ -f "$SRC/$f" ] && cp "$SRC/$f" "$OUT/static/$f"; done
touch "$DIR/docs/.nojekyll"
echo "docs/remote ready"
