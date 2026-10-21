# 🤖 UNIVERSAL AI AUTO-INSTALLER PROMPT FOR ANDROID TV

> **How to use this prompt:**
> Copy the entire block below and paste it directly into **ChatGPT**, **Claude**, **Gemini**, or **DeepSeek**.
> Connect your computer to your TV via Wi-Fi ADB (`adb connect <YOUR_TV_IP>:5555`) or USB, and let the AI guide and execute the complete transformation of your TV into an ultra-fast, ad-free Smart TV with the 3D Parallax CoverFlow Launcher.

---

```markdown
You are an expert Senior Android TV Systems & Firmware Engineer. 
I want you to automatically optimize my Smart TV, eliminate all manufacturer bloatware, expand RAM using zRAM/storage swap, configure Private DNS (DoT) to block ISP throttling and ads, install the 3D Liquid Glass Parallax Launcher (TV Master Launcher), configure Ad-Free YouTube disguised as the official YouTube app, and establish 5 fast movie sources with daily vitrin auto-updates.

Follow this systematic, step-by-step procedure. Prompt me after each phase or execute the commands if running inside an automated terminal agent:

---

### STEP 1: TV DISCOVERY & HARDWARE PROFILE PROBE
Execute these diagnostic commands via ADB to fingerprint my TV hardware:

```bash
echo "=== TV HARDWARE IDENTIFICATION ==="
adb devices
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.brand
adb shell getprop ro.product.model
adb shell getprop ro.product.cpu.abi
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell wm size
adb shell wm density
adb shell free -m
```

Based on the output:
- Identify if the TV is 32-bit (`armeabi-v7a`) or 64-bit (`arm64-v8a`).
- Detect the manufacturer: Arçelik, Beko, Grundig, TCL, Sony, Philips, Xiaomi, Hisense, or Chromecast with Google TV.
- Proceed to Step 2 with the matching brand profile.

---

### STEP 2: MULTI-BRAND SAFE DEBLOAT
Safely remove OEM telemetry, storefront ads, background bloat, and heavy stock launchers without breaking HDMI inputs, DVB-S2 satellite tuners, or picture settings.

Run the appropriate brand profile for user 0:

#### Profile A: Arçelik / Beko / Grundig (MediaTek SoC)
```bash
for pkg in \
  "mediatek.factorymenu.ui" "arcelik.factorymenu.hatone" "com.mediatek.tv.factory" \
  "com.mediatek.wwtv.webview" "com.mediatek.wwtv.mediaplayer" "com.mediatek.wwtv.tvcenter" \
  "com.mediatek.wwtv.tvcenter.rro" "tr.com.bein.connect.tv" "io.freemytv" \
  "com.arcelik.ChannelEditor" "com.arcelik.tv.rctest" \
  "com.google.android.tvlauncher" "com.google.android.apps.tv.launcherx" \
  "com.google.android.tungsten.setupwraith" "com.google.android.feedback" \
  "com.google.android.play.games"; do
    adb shell "pm uninstall -k --user 0 $pkg 2>/dev/null || pm disable-user --user 0 $pkg 2>/dev/null || true"
done
```

#### Profile B: TCL / Thomson / FFALCON
```bash
for pkg in \
  "com.tcl.useragreement" "com.tcl.tvmanager" "com.tcl.partnercustomizer" \
  "com.tcl.waterfall" "com.tcl.messagebox" "com.tcl.sourcemanager" \
  "com.tcl.bi" "com.tcl.cyberui" "com.tcl.appmarket" "com.tcl.videoplayer" \
  "com.google.android.tvlauncher" "com.google.android.apps.tv.launcherx"; do
    adb shell "pm uninstall -k --user 0 $pkg 2>/dev/null || pm disable-user --user 0 $pkg 2>/dev/null || true"
done
```

#### Profile C: Sony Bravia
```bash
for pkg in \
  "com.sony.dtv.bravialifehack" "com.sony.dtv.demomode" "com.sony.dtv.smarthelp" \
  "com.sony.dtv.telemetry" "com.sony.dtv.networkservice" "com.sony.dtv.recommendations" \
  "com.google.android.tvlauncher" "com.google.android.apps.tv.launcherx"; do
    adb shell "pm uninstall -k --user 0 $pkg 2>/dev/null || pm disable-user --user 0 $pkg 2>/dev/null || true"
done
```

#### Profile D: Philips Android TV
```bash
for pkg in \
  "org.droidtv.demomode" "org.droidtv.recommendations" "org.droidtv.nettvregistration" \
  "org.droidtv.philipstvprovider" "com.google.android.tvlauncher" "com.google.android.apps.tv.launcherx"; do
    adb shell "pm uninstall -k --user 0 $pkg 2>/dev/null || pm disable-user --user 0 $pkg 2>/dev/null || true"
done
```

#### Profile E: Xiaomi Mi TV / PatchWall / Redmi
```bash
for pkg in \
  "com.mitv.gallery" "com.xiaomi.mitv.upgrade" "com.mitv.videoplayer" \
  "com.xiaomi.mitv.advertise" "com.mitv.tvhome" "com.xiaomi.mitv.shop" \
  "com.xiaomi.devicestatistics" "com.google.android.tvlauncher"; do
    adb shell "pm uninstall -k --user 0 $pkg 2>/dev/null || pm disable-user --user 0 $pkg 2>/dev/null || true"
done
```

#### Profile F: Generic Android TV / Chromecast with Google TV
```bash
for pkg in \
  "com.google.android.tvlauncher" "com.google.android.apps.tv.launcherx" \
  "com.google.android.tungsten.setupwraith" "com.google.android.feedback" \
  "com.google.android.play.games" "com.google.android.videos"; do
    adb shell "pm uninstall -k --user 0 $pkg 2>/dev/null || pm disable-user --user 0 $pkg 2>/dev/null || true"
done
```

---

### STEP 3: SMART RAM & STORAGE-TO-RAM EXPANSION
Eliminate TV UI stutter and out-of-memory app crashes:

```bash
# 1. Enable Purgeable Assets & Aggressive Memory Reclaim
adb shell "setprop persist.sys.purgeable_assets 1"

# 2. Configure 1GB LZ4 zRAM Swap Compressed Disk
adb shell "echo 1 > /sys/block/zram0/reset 2>/dev/null || true"
adb shell "echo lz4 > /sys/block/zram0/comp_algorithm 2>/dev/null || true"
adb shell "echo 1073741824 > /sys/block/zram0/disksize 2>/dev/null || true"
adb shell "mkswap /dev/block/zram0 2>/dev/null || true"
adb shell "swapon /dev/block/zram0 2>/dev/null || true"

# 3. Tune Swappiness & Cache Pressure
adb shell "sysctl -w vm.swappiness=80 2>/dev/null || true"
adb shell "sysctl -w vm.vfs_cache_pressure=100 2>/dev/null || true"

# 4. Storage-to-RAM Expansion (Android 11+)
adb shell "settings put global ram_expand_size_list 2048 2>/dev/null || true"
adb shell "setprop persist.sys.ram_expand.size 2048 2>/dev/null || true"

# 5. Low Memory Killer (LMK) & Trim Tuning
adb shell "setprop sys.sysctl.extra_free_kbytes 24300"
adb shell "setprop persist.sys.fw.trim_empty_apps true"
adb shell "setprop persist.sys.fw.empty_app_percent 50"

# 6. Flush inactive cache
adb shell "sync; echo 3 > /proc/sys/vm/drop_caches 2>/dev/null || true"
```

---

### STEP 4: NETWORK & PRIVATE DNS OVER TLS (DoT)
Bypass ISP DNS tampering/poisoning (such as Turkish ISP DNS redirects) and block tracking:

```bash
adb shell "settings put global private_dns_mode hostname"
adb shell "settings put global private_dns_specifier dns.google"
# Verify:
adb shell "settings get global private_dns_specifier"
```

---

### STEP 5: INSTALL TV MASTER LAUNCHER (V2.0)
Install the ultra-lightweight, hardware-accelerated 3D Parallax CoverFlow Launcher:

```bash
# Clone the repository or download the prebuilt APK from release/
# adb install -r -d -g release/tv-master-launcher-v2.0.apk
adb install -r -d -g release/tv-master-launcher-v2.0.apk

# Set as default Home Launcher:
adb shell "cmd package set-home-activity com.cleanyourtv.launcher/.MainActivity"
```

---

### STEP 6: AD-FREE YOUTUBE WITH OFFICIAL LOGO DISGUISE
Install ad-free playback (SmartTube / TizenTube / Cobalt) and disguise it as official YouTube:

```bash
# 1. Disable official bloated YouTube:
adb shell "pm uninstall -k --user 0 com.google.android.youtube.tv 2>/dev/null || pm disable-user --user 0 com.google.android.youtube.tv 2>/dev/null || true"

# 2. TV Master Launcher includes built-in intent redirection:
# The launcher tile displays the authentic YouTube logo and name 'YouTube',
# routing directly to io.gh.reisxd.tizentube.cobalt or com.teamsmart.videomanager.tv with 0 ads.
```

---

### STEP 7: MOVIE SOURCES SETUP (TURKSINEMA 5 PROVIDERS)
Setup CloudStream 3 with the 5 curated Turkish movie providers:

```bash
# 1. Add repository:
adb shell "am start -a android.intent.action.VIEW -d \"cloudstreamrepo://raw.githubusercontent.com/Wiojelt/TurkSinema/main/repo.json\""

# 2. In CloudStream settings, keep ONLY:
# - Webteizle
# - Filmmodu
# - FilmMakinesi
# - HDFilmCehennemi
# - FullHDFilmizlesene
# (All other providers uninstalled for optimal load speeds)

# 3. TV Master Launcher automatically queries these providers, extracts focal neon glow colors,
# and rotates the vitrin catalog daily at 00:00 midnight!
```

---

### STEP 8: AIRPLAY RECEIVER SETUP
Enable AirPlay mirroring from iPhones, iPads, and MacBooks:
```bash
# Whitelist background battery usage for cast receiver:
adb shell "dumpsys deviceidle whitelist +com.instantbits.cast.receiver 2>/dev/null || true"
adb shell "dumpsys deviceidle whitelist +com.ionitech.airscreen 2>/dev/null || true"
adb shell "settings put global stay_on_while_plugged_in 3 2>/dev/null || true"
```

---

### STEP 9: VERIFICATION & BENCHMARK
Verify the entire system:
```bash
echo "=== SYSTEM VERIFICATION ==="
adb shell "dumpsys activity activities | grep ResumedActivity"
adb shell "dumpsys meminfo com.cleanyourtv.launcher | grep 'TOTAL PSS:'"
adb shell "dumpsys gfxinfo com.cleanyourtv.launcher framestats | tail -n 15"
```
Ensure 0 dropped frames, total RAM usage under 35 MB, and instant D-Pad responsiveness.
```
