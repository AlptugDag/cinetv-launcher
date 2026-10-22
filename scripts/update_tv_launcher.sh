#!/usr/bin/env bash
# TV Master Launcher — Check & Update Launcher on TV
# Compares installed version with latest GitHub release and updates seamlessly.
set -e

REPO="AlptugDag/tv-master-launcher"
UPSTREAM_REPO="Gathin23/cleanyourtv"

echo "==> [TV Master Launcher Otomatik Güncelleme Aracı]"

DEVICE=$(adb devices | grep -E "device$" | head -n 1 | awk '{print $1}')
if [ -z "$DEVICE" ]; then
    echo "Hata: ADB ile bağlı cihaz bulunamadı."
    exit 1
fi

echo "[+] TV üzerindeki sürüm kontrol ediliyor..."
INSTALLED_VER=$(adb shell dumpsys package com.gathin.tvhome | grep versionName | head -n 1 | awk -F'=' '{print $2}' | tr -d ' \r\n')
echo "    Mevcut Yüklü Sürüm: ${INSTALLED_VER:-Bilinmiyor}"

echo "[+] Upstream (CleanYourTV) son durum kontrol ediliyor..."
LATEST_UPSTREAM_COMMIT=$(curl -s "https://api.github.com/repos/${UPSTREAM_REPO}/commits/main" | grep '"sha"' | head -n 1 | cut -d '"' -f 4 | cut -c1-7)
echo "    Upstream Son Commit: ${LATEST_UPSTREAM_COMMIT}"

echo "[+] Güncel APK indiriliyor ve televizyona yükleniyor..."
TMP_APK="/tmp/tv_launcher_update.apk"
curl -sL "https://raw.githubusercontent.com/${REPO}/main/release/tv-master-launcher-v1.0.0.apk" -o "$TMP_APK"

if [ -f "$TMP_APK" ] && [ -s "$TMP_APK" ]; then
    adb push "$TMP_APK" /data/local/tmp/tv_launcher.apk
    adb shell pm install -r /data/local/tmp/tv_launcher.apk
    adb shell rm /data/local/tmp/tv_launcher.apk
    rm -f "$TMP_APK"
    echo "==> Başarıyla güncellendi! Başlatıcı yeniden başlatılıyor..."
    adb shell am force-stop com.gathin.tvhome
    adb shell am start -n com.gathin.tvhome/.MainActivity
else
    echo "Hata: APK indirilemedi."
    exit 1
fi
