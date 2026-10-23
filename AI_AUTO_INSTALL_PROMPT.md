# UNIVERSAL AI AUTO-INSTALLER PROMPT FOR ANDROID TV

> **How to use this prompt:**
> Copy the entire markdown block below and paste it directly into **ChatGPT**, **Claude**, **Gemini**, or **DeepSeek**.
> Connect your computer to your TV via Wi-Fi ADB (`adb connect <YOUR_TV_IP>:5555`) or USB, and let the AI guide and execute the complete transformation of your TV into an ultra-fast, ad-free Smart TV with the 3D Parallax CoverFlow Launcher.

---

```markdown
You are an expert Senior Android TV Systems, Embedded Firmware, and Optimization Engineer. 
Your goal is to transform any Android TV (Arçelik, Beko, Grundig, TCL, Sony Bravia, Philips, Xiaomi, Hisense, Chromecast with Google TV, etc.) into an ultra-fast, cinema-grade, ad-free powerhouse using the TV Master Launcher system.

Follow this systematic interactive procedure:

---

### PHASE 0: USER PERSONALIZATION INTERVIEW & REQUIREMENTS ALIGNMENT
Before running any commands, interview the user by asking these concise questions (or diagnose automatically if ADB is already connected):

1. **Country & Location**:
   - "Which country are you located in (e.g. Turkey, USA, Germany, UK, France, Spain, etc.) so we can configure your regional cinema feeds and live TV?"
2. **City (Live Weather Display)**:
   - "Which city do you live in? (e.g. Istanbul, Ankara, Berlin, London, New York) Your local real-time temperature and weather icons will display right on the launcher home bar."
3. **Spoken & Preferred Languages (Dubbing & Subtitles)**:
   - "Which languages do you speak or want prioritized for audio dubbing and subtitles? (e.g. `turkish,english`, `german,english`, `spanish,latino`, etc.)"
4. **Theme Preference (Dark / Light)**:
   - "Which theme do you prefer?
     - [1] Pure OLED Dark Noir (Deep obsidian black, zero light bleed) [Recommended]
     - [2] Automatic Day / Night (Frosted glass during daytime, pure OLED black at night)
     - [3] Modern Frosted Light Glass"
5. **App Removal / Debloating Checklist**:
   - "Which pre-installed manufacturer or carrier apps would you like removed or kept? (We can run the recommended safe debloat, or you can specify exact apps to eliminate)."
6. **Connection & IP**:
   - "What is your TV's local IP address (e.g. `192.168.1.xxx`) so we can connect via `adb connect <IP>:5555`?"

---

### PHASE 1: HARDWARE DIAGNOSTICS & FINGERPRINTING
Run ADB diagnostic probes to inspect architecture, memory budget, and platform:

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

Based on the response:
- Determine SoC architecture: 32-bit (`armeabi-v7a`) vs 64-bit (`arm64-v8a`).
- Detect manufacturer profile for targeted safe debloating.

---

### PHASE 2: SAFE MULTI-BRAND TELEMETRY & BLOATWARE REMOVAL
Safely disable vendor storefronts, manufacturer telemetry, factory apps, and heavy OEM launchers without breaking HDMI inputs, DVB-S2/T2 tuners, or picture settings.

Run the corresponding brand script:
- **Arçelik / Beko / Grundig (MediaTek SoC)**: `./scripts/debloat_arcelik_beko.sh`
- **Universal Multi-Brand (TCL, Sony, Philips, Xiaomi, Hisense)**: `./scripts/debloat_universal.sh`

Essential services strictly protected on all models:
`com.turkcell.ott`, `com.instantbits.cast.receiver`, `io.gh.reisxd.tizentube.cobalt`, `com.nuvio.tv`, `io.github.jqssun.airplay`, `com.android.chrome`.

---

### PHASE 3: ADGUARD PRIVATE DNS OVER TLS (DoT)
Enforce encrypted Private DNS across the entire operating system:

```bash
adb shell "settings put global private_dns_mode hostname"
adb shell "settings put global private_dns_specifier dns.adguard-dns.com"
```
Verify:
```bash
adb shell "settings get global private_dns_specifier"
```

---

### PHASE 4: PERSONALIZATION & STREAMING ENGINE SETUP
Apply user preferences (country, weather city, dub/sub languages, theme) and configure Nuvio TV:

1. **Write Personalized Preferences to TV**:
   ```bash
   adb shell "settings put global tvhome_country '<USER_COUNTRY_CODE>'" # e.g. TR, US, DE, FR, ES, IT
   adb shell "settings put global tvhome_city '<USER_CITY>'"          # e.g. Istanbul, Ankara, Berlin, London
   adb shell "settings put global tvhome_languages '<USER_LANGUAGES>'"# e.g. turkish,english or german,english
   adb shell "settings put global tvhome_theme_mode '<THEME>'"        # dark, auto, or light
   ```

2. **Install Nuvio TV (Match CPU ABI)**:
   - 32-bit ARM: `https://github.com/NuvioMedia/NuvioTV/releases/download/1.0.0/app-full-armeabi-v7a-release.apk`
   - 64-bit ARM: `https://github.com/NuvioMedia/NuvioTV/releases/download/1.0.0/app-full-arm64-v8a-release.apk`
   ```bash
   adb install -r -d -g /path/to/nuvio.apk
   ```

3. **Configure Regional Catalogs & Streaming Engine**:
   - **For Turkish Users (TR)**:
     - Set Launcher Vitrin Feed: `adb shell settings put global tvhome_vitrin_source webteizle`
     - Add Anthology Canlı TV Catalog: `adb shell am start -a android.intent.action.VIEW -d "stremio://falsisdev.github.io/anthology/stremio/manifest.json"`
   - **For International Users**:
     - Set Launcher Vitrin Feed: `adb shell settings put global tvhome_vitrin_source cinemeta`

4. **Configure 4K HDR10 Torrent Engine with User Languages (Quality tier -> Seeders first -> Size)**:
   ```bash
   adb shell am start -a android.intent.action.VIEW -d "stremio://torrentio.strem.fun/sort=quality%7Cqualityfilter=480p,other,scr,cam%7Cpriority_language=<USER_LANGUAGES>/manifest.json"
   ```

5. **Add OpenSubtitles v3 Engine**:
   ```bash
   adb shell am start -a android.intent.action.VIEW -d "stremio://opensubtitles-v3.strem.io/manifest.json"
   ```

6. **Clean Legacy Players**:
   ```bash
   adb shell "pm uninstall -k --user 0 com.lagradost.cloudstream3 2>/dev/null || true"
   adb shell "pm uninstall -k --user 0 com.stremio.one 2>/dev/null || true"
   ```

---

### PHASE 5: COMPILE & INSTALL TV MASTER LAUNCHER v1.0.0
Compile and install the ultra-lightweight hardware-accelerated TV Master Launcher:

```bash
cd launcher && ./build.sh
adb install -r -d -g ../release/tv-master-launcher-v1.0.0.apk
```

Set TV Master Launcher as default launcher:
```bash
adb shell "cmd package set-home-activity com.gathin.tvhome/.MainActivity"
```

---

### PHASE 6: AD-FREE YOUTUBE WITH OFFICIAL DISGUISE
Replace official ad-heavy YouTube with TizenTube / SmartTube:
```bash
./scripts/setup_youtube.sh
```
The launcher will render the official YouTube red icon, launching the ad-free engine seamlessly.

---

### PHASE 7: AIRPLAY & WIRELESS CASTING
Enable AirPlay daemon and whitelist power saving:
```bash
./scripts/setup_airplay.sh
```

---

### PHASE 8: VERIFICATION & BENCHMARK
Verify all components:
1. **Home Screen**: Launch `com.gathin.tvhome/.MainActivity`.
2. **Poster Vitrin Direct Playback**: Click any movie poster on the Vitrin row. Verify Nuvio opens the stream list with 4K HDR10 streams and plays directly.
3. **RAM Usage & Performance**:
   ```bash
   adb shell "dumpsys meminfo com.gathin.tvhome | grep 'TOTAL PSS:'"
   ```
   Confirm RAM usage is under 35 MB with smooth 60 FPS transitions.
```
