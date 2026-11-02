#!/bin/bash
# CineTV for Mac (and Linux): double-click (Mac) or run it. Gets Google's adb and CineTV's installer into
# ~/Library/Application Support/CineTV (Linux: ~/.local/share/CineTV), then starts the installer, which
# asks for the TV's address and the rest.
set -e
case "$(uname)" in
    Darwin) W="$HOME/Library/Application Support/CineTV"; PT=darwin ;;
    *) W="$HOME/.local/share/CineTV"; PT=linux ;;
esac
mkdir -p "$W"
cd "$W"
if ! command -v adb >/dev/null 2>&1; then
    if [ ! -x platform-tools/adb ]; then
        echo "  adb indiriliyor (Google platform-tools)..."
        curl -fsSL "https://dl.google.com/android/repository/platform-tools-latest-$PT.zip" -o pt.zip
        unzip -qo pt.zip && rm pt.zip
    fi
    export PATH="$W/platform-tools:$PATH"
fi
echo "  CineTV kurulum dosyaları indiriliyor..."
rm -rf cinetv-launcher-main
curl -fsSL https://codeload.github.com/AlptugDag/cinetv-launcher/tar.gz/refs/heads/main | tar xz
cd cinetv-launcher-main
chmod +x install.sh scripts/*.sh 2>/dev/null || true
./install.sh "$@"
echo
read -r -p "  Kapatmak için Enter'a basın " _
