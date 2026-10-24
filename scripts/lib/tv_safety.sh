#!/usr/bin/env bash
# CineTV — shared safety helpers (source this file, do not execute it).
#
# Why this exists:
#   Removing the OEM "TV core" (e.g. com.mediatek.wwtv.tvcenter on Arçelik/Beko/Grundig MediaTek
#   TVs, com.tcl.tvmanager on TCL, ...) breaks tuner/HDMI/picture/HDR plumbing. Debloaters must
#   NEVER touch these, no matter which list or interactive input asks for them.

# Returns 0 if the package is a protected TV-core / hardware package.
is_protected_pkg() {
    case "$1" in
        # --- Android / Google core ---
        android|com.android.systemui|com.android.settings|com.android.settings.*|\
        com.android.tv.settings*|com.android.tv|com.android.tv.*|\
        com.android.providers.settings|com.android.providers.media*|com.android.providers.tv|\
        com.android.providers.downloads*|com.android.providers.telephony|com.android.externalstorage|\
        com.android.bluetooth|com.android.inputdevices|com.android.shell|com.android.vending|\
        com.android.permissioncontroller|com.google.android.permissioncontroller|\
        com.google.android.gms|com.google.android.gsf|com.google.android.webview|com.android.webview|\
        com.google.android.tv.remote.service|com.google.android.tv.*|com.google.android.inputmethod.*|\
        com.google.android.tts)
            return 0 ;;
        # --- MediaTek / Mstar TV core (Arçelik, Beko, Grundig, Vestel, Philips-MTK, ...) ---
        com.mediatek.wwtv.tvcenter|com.mediatek.wwtv.tvcenter.*|com.mediatek.tv.*|com.mediatek.tvinput*|\
        com.mediatek.*tvapi*|com.mediatek.*display*|com.mediatek.*hdmi*|com.mstar.android.tv.*|\
        com.mstar.tv.*|com.mstar.*display*|com.mstar.*hdmi*)
            return 0 ;;
        # --- Other OEM TV managers / input / source / picture services ---
        com.tcl.tvmanager|com.tcl.sourcemanager|com.tcl.cyberui|com.tcl.partnercustomizer|\
        com.sony.dtv.networkservice|com.sony.dtv.tvinput*|com.sony.dtv.*picture*|\
        org.droidtv.tvpowermanager|org.droidtv.channels|org.droidtv.philipstvprovider|org.droidtv.tv*|\
        com.xiaomi.mitv.tvservice*|com.hisense.tv.service*|com.jamdeo.tv.service*)
            return 0 ;;
        # --- Anything that smells like HDMI / display / HDR / picture / tuner / input plumbing ---
        *hdmi*|*tvinput*|*tvapi*|*tvservice*|*display*|*hdr*|*panel*|*picture*|*tuner*|*dvb*|*cec*)
            return 0 ;;
        # --- This project ---
        com.cinetv.launcher|com.nuvio.tv|org.courville.nova|io.github.jqssun.airplay)
            return 0 ;;
    esac
    return 1
}

# Cache of installed packages (fetched once; "pm list packages" is slow over ADB).
_TV_PKG_CACHE=""
refresh_pkg_cache() {
    _TV_PKG_CACHE="$(adb shell "pm list packages" 2>/dev/null | tr -d '\r')"
}

# Exact-match check (NOT substring: "tvcenter" must not match "tvcenter.rro").
pkg_installed() {
    [ -n "$_TV_PKG_CACHE" ] || refresh_pkg_cache
    printf '%s\n' "$_TV_PKG_CACHE" | grep -qxF "package:$1"
}

# Does a global/secure/system setting key already exist on this device?
setting_exists() {
    local ns="$1" key="$2" val
    val="$(adb shell "settings get $ns $key" 2>/dev/null | tr -d '\r')"
    [ -n "$val" ] && [ "$val" != "null" ]
}

# Write a setting only if the key is already known to this firmware (never invent vendor keys).
put_setting_if_exists() {
    local ns="$1" key="$2" value="$3"
    if setting_exists "$ns" "$key"; then
        adb shell "settings put $ns $key $value" >/dev/null 2>&1 || true
        echo "    [+] $ns/$key = $value"
    else
        echo "    [~] $ns/$key not present on this firmware — skipped"
    fi
}

# Safe removal: refuses protected packages, prefers the reversible path.
#   uninstall -k --user 0  (keeps data; undo: adb shell cmd package install-existing <pkg>)
#   disable-user --user 0  (undo: adb shell pm enable <pkg>)
# Returns 0 if removed/disabled, 1 if skipped.
safe_remove_pkg() {
    local pkg="$1"
    if is_protected_pkg "$pkg"; then
        echo "[!] Skipping protected TV-core package: $pkg"
        return 1
    fi
    if ! pkg_installed "$pkg"; then
        return 1
    fi
    adb shell "pm uninstall -k --user 0 $pkg 2>/dev/null || pm disable-user --user 0 $pkg 2>/dev/null || true" >/dev/null 2>&1 || true
    return 0
}
