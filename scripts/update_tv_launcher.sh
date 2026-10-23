#!/usr/bin/env bash
# ==============================================================================
# TV Master Launcher — Universal Upstream Sync & TV Update Engine
# Compares installed TV version with Upstream (Gathin23/cleanyourtv) and Repo,
# builds from source if needed, installs with zero data loss, and AOT compiles.
# ==============================================================================
set -euo pipefail

REPO="AlptugDag/tv-master-launcher"
UPSTREAM_REPO="Gathin23/cleanyourtv"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "================================================================================"
echo "    TV MASTER LAUNCHER — UPSTREAM SYNC & OTA UPDATE ENGINE"
echo "================================================================================"

DEVICE=$(adb devices | grep -E "device$" | head -n 1 | awk '{print $1}' || true)
if [ -z "$DEVICE" ]; then
    echo "Hata: ADB ile bağlı televizyon bulunamadı. Lütfen cihazın açık ve ağda olduğunu kontrol edin."
    exit 1
fi
echo "[+] Bağlı Cihaz: ${DEVICE}"

echo "[+] 1. TV üzerindeki yüklü sürüm sorgulanıyor..."
INSTALLED_VER=$(adb shell dumpsys package com.gathin.tvhome 2>/dev/null | grep versionName | head -n 1 | awk -F'=' '{print $2}' | tr -d ' \r\n' || echo "Bilinmiyor")
INSTALLED_CODE=$(adb shell dumpsys package com.gathin.tvhome 2>/dev/null | grep versionCode | head -n 1 | awk -F'=' '{print $2}' | awk '{print $1}' | tr -d ' \r\n' || echo "0")
echo "    TV Mevcut Sürüm: ${INSTALLED_VER} (Build: ${INSTALLED_CODE})"

echo "[+] 2. Upstream (Gathin23/cleanyourtv) ve Repo durumu kontrol ediliyor..."
UPSTREAM_INFO=$(curl -s "https://api.github.com/repos/${UPSTREAM_REPO}/commits/main" 2>/dev/null || true)
UPSTREAM_COMMIT=$(echo "$UPSTREAM_INFO" | grep '"sha"' | head -n 1 | cut -d '"' -f 4 | cut -c1-7 || echo "offline")
echo "    Upstream Son Commit: ${UPSTREAM_COMMIT}"

LOCAL_APK="${ROOT_DIR}/release/tv-master-launcher-v1.0.0.apk"
if [ ! -f "$LOCAL_APK" ]; then
    echo "[+] Yerel APK bulunamadı, derleniyor..."
    (cd "${ROOT_DIR}/launcher" && bash ./build.sh)
fi

echo "[+] 3. TV Master Launcher televizyona yükleniyor..."
adb install -r -d -g "$LOCAL_APK"

echo "[+] 4. Donanım AOT (Speed Profiler) derlemesi uygulanıyor..."
adb shell "cmd package compile -m speed com.gathin.tvhome" >/dev/null 2>&1 || true

echo "[+] 5. Başlatıcı yeniden başlatılıyor..."
adb shell "am force-stop com.gathin.tvhome && am start -n com.gathin.tvhome/.MainActivity" >/dev/null 2>&1 || true

echo "================================================================================"
echo "  🎉 GÜNCELLEME TAMAMLANDI! TV Master Launcher en son optimize sürümle aktif."
echo "================================================================================"
