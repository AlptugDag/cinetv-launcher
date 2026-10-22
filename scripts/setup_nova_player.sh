#!/usr/bin/env bash
# TV Master Launcher — Nova Video Player Setup & Integration
# Automatically downloads and installs the official Nova Video Player APK from GitHub releases.
# Serves as the ultimate hardware-accelerated fallback player for Nuvio TV (AFR + Bitstream Audio).
set -e

echo "==> [Nova Video Player Installer]"
DEVICE=$(adb devices | grep -E "device$" | head -n 1 | awk '{print $1}')
if [ -z "$DEVICE" ]; then
    echo "ERROR: No ADB device connected."
    exit 1
fi

echo "==> Detecting TV CPU Architecture..."
ABI=$(adb shell getprop ro.product.cpu.abi | tr -d '\r')
echo "    Architecture: $ABI"

NOVA_VERSION="v6.5.5"
BASE_URL="https://github.com/nova-video-player/aos-AVP/releases/download/${NOVA_VERSION}"

case "$ABI" in
    arm64-v8a)
        APK_NAME="org.courville.nova-2672099-6.5.5-20261009.2113-arm64-v8a-release.apk"
        ;;
    armeabi-v7a|armeabi)
        APK_NAME="org.courville.nova-2672099-6.5.5-20261009.2113-armeabi-v7a-release.apk"
        ;;
    *)
        APK_NAME="org.courville.nova-2672099-6.5.5-20261009.2113-universal-release.apk"
        ;;
esac

APK_URL="${BASE_URL}/${APK_NAME}"
TMP_APK="/tmp/${APK_NAME}"

echo "==> Downloading Nova Video Player (${NOVA_VERSION} - ${ABI})..."
curl -sL "$APK_URL" -o "$TMP_APK"

echo "==> Installing Nova Video Player onto TV..."
adb install -r "$TMP_APK"

echo "==> Nova Video Player installed successfully!"
echo ""
echo "================================================================================"
echo "💡 İPUCU: NUVIO'DA HARİCİ OYNATICI (NOVA PLAYER) KULLANIMI"
echo "================================================================================"
echo "Nuvio TV üzerinden bir film veya dizi izlerken herhangi bir oynatma hatası,"
echo "ses senkron problemi veya desteklenmeyen format uyarısı alırsanız:"
echo ""
echo "1) Nuvio Ayarlar -> Oynatıcı menüsüne girerek 'Harici Oynatıcı' seçeneğini açın."
echo "2) Veya yayının üzerindeyken kumandanın 'Seç/OK' tuşuna uzun basın ve"
echo "   'Harici Oynatıcı ile Aç' -> 'Nova Video Player'ı seçin."
echo ""
echo "Nova Player, TV donanımının el verdiği en yüksek çözünürlükte, otomatik kare hızı"
echo "eşleme (AFR) ve passthrough ses desteği (Dolby Atmos / DTS) ile yayını akıcı oynatacaktır."
echo "================================================================================"
