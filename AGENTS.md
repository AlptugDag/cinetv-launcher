# AGENTS.md — installing CineTV for someone, as an AI agent

You are helping a person turn their Android TV / Google TV into CineTV, exactly as the author runs it.
Everything here works from a computer on the same network as the TV, over `adb`. Nothing needs root.

Ask the person only what you cannot find out yourself: the TV's IP address (if discovery fails), their city,
and their API keys. Never print keys, never pass them as command-line arguments, never write them to a file in
this repository.

---

## 0. Before you start

- Tools on the computer: `adb` (Android platform-tools), `curl`, `bash`. On macOS: `brew install android-platform-tools`.
- On the TV (the person does this with the remote; tell them step by step):
  1. *Settings › Device preferences › About*: press **Build** 7 times ("You are now a developer").
  2. *Settings › Device preferences › Developer options*: turn on **USB debugging** and, if present,
     **Network debugging** / **Wireless debugging**.
  3. Note the IP address in *Settings › Network*.
- Connect: `adb connect <TV_IP>:5555`. The TV shows *Allow USB debugging?*: the person ticks **Always allow**
  and presses **OK**. `adb devices` must then show the TV as `device`, not `unauthorized` or `offline`.
  If it goes `offline` later: `adb kill-server && adb connect <TV_IP>:5555`.

## 1. The identical setup (one command)

```bash
git clone https://github.com/AlptugDag/cinetv-launcher && cd cinetv-launcher
CINETV_TMDB_KEY=... CINETV_AI_KEY=... CINETV_MDBLIST_KEY=... \
  ./install.sh --tv <TV_IP> --city "<City>" --theme cinetv --apps --youtube --nuvio --debloat --yes
```

This is the author's own configuration. What each flag does:

| Flag | Effect |
| :--- | :--- |
| `--tv HOST[:PORT]` | the TV (Wi-Fi adb) |
| `--city NAME` | weather next to the clock and the mood shelf ("Pazar gecesi için suç filmleri") |
| `--theme ID` | `cinetv` (Alptug Default) · `glass` · `aurora` · `scenes` · `dussehra` (Royal Gold) · `six-columns` (Cinema Wall) · `oled` |
| `--apps` | Nuvio, Nova Video Player, AirPlay receiver, from their official GitHub releases |
| `--youtube` | ad-free YouTube (TizenTube); the stock app is switched off, reversibly |
| `--nuvio` | Nuvio questionnaire and addons (see §3) |
| `--debloat` | safe debloat (see §4) |
| `--yes` | no questions; defaults for anything not given |

Leave out a flag the person does not want. Without `--yes` the installer asks.

**API keys**, all optional and all also enterable later on the TV in *Settings › AI & films*:

| Variable | What it unlocks | Where the person gets it |
| :--- | :--- | :--- |
| `CINETV_TMDB_KEY` (or `CINETV_TMDB_TOKEN`) | localized titles, posters, plots, people | themoviedb.org › Settings › API |
| `CINETV_AI_KEY` | AI search, answers, the mood shelf | any of Gemini (aistudio.google.com), Claude, OpenAI, Grok (xAI), OpenRouter; the key's format picks the provider |
| `CINETV_MDBLIST_KEY` | ratings from IMDb, Rotten Tomatoes, Letterboxd | mdblist.com › Preferences › API |

Keys are handed to the TV once and moved into its hardware-backed encrypted store; they cannot be read back.

## 2. Check it

```bash
./install.sh --tv <TV_IP> --verify
```

Every line must say PASS (WARN lines are optional features). Then press **Home** on the remote: CineTV appears.
If the TV's own launcher comes back after a reboot, run the install again; it re-applies Home.

## 3. Nuvio (films and series)

`--nuvio` runs `scripts/setup_nuvio.sh`. It asks: content language, title/plot language, subtitle languages,
autoplay, best quality, qualities to hide, favourite and hidden genres and countries. With `--yes` it uses
Turkish defaults (`--defaults tr`). It then installs addons straight into Nuvio through its install links:

- **Torrentio**, configured from the answers (languages first, quality ceiling, hidden qualities);
- **TR Altyazı** (Turkish subtitles);
- **AIOMetadata**, **Xperience**, **Kaptain's Collection** when the person pastes their manifest URLs.

The answers are saved to `nuvio-preferences.json`. Some of Nuvio's screens have no link or command, so you finish
them on the TV by remote keys. Take a screenshot after each step and read it before the next key:

```bash
adb exec-out screencap -p > shot.png          # look at the screen
adb shell input keyevent 19|20|21|22          # up | down | left | right
adb shell input keyevent 23                   # OK
adb shell input keyevent 4                    # back
adb shell input text 'https://...'            # type into the focused field
```

1. Open Nuvio: `adb shell monkey -p com.nuvio.tv 1`.
2. **Plugin repositories**: *Settings › Plugins › Add repository*, paste each URL from
   `plugin_repositories` in `nuvio-preferences.json` and confirm:
   - `https://raw.githubusercontent.com/falsisdev/anthology/main/manifest.json`
   - `https://raw.githubusercontent.com/Wiojelt/TurkSinema/main/repo.json`
3. **Playback**: subtitle language and second subtitle language, autoplay first source, as saved.
4. **Appearance / Language**: `metadata_language`.
5. **External player**: Nova Video Player (if `--apps` installed it), so casting and Nuvio use the same player.

## 4. Safe debloat (what `--debloat` will and will not do)

- Only advertising, telemetry and promo apps the maker pre-installed, chosen by brand
  (`scripts/debloat_universal.sh`: Arçelik/Beko/Grundig, TCL, Sony, Philips, Xiaomi, Hisense…).
- Never the TV core: tuner, HDMI, picture, sound, inputs, CEC, Play services, keyboard, Bluetooth.
- Never an app opened in the last 30 days.
- Apps are **switched off**, not uninstalled; every change goes to `debloat-journal.txt` (and a copy on the TV
  at `/sdcard/cinetv-debloat.txt`). `scripts/restore_all_tv_apps.sh` undoes exactly those.
- Emergency (no picture, no inputs): `scripts/restore_tv_core.sh`.

Tell the person before you run it, in one sentence, and run it only if they agree.

## 5. The phone

Once installed, the phone button at the top of Home shows an address and a QR code. The person opens it on their phone
(same Wi-Fi), presses **Allow** on the TV once, and the phone is remembered. The page is HTTPS
(`https://<ip-with-dashes>.local-ip.sh:9443`) and can be added to the home screen. It has search (Enter shows the
results on the TV too), a remote, casting of any video link, and a background-photo upload.

## 6. On the TV itself

- **Optimize & test** (in Settings): freezes cached apps, shortens animations, frees memory. Safe to run any time.
- **Performance**: a picker; *Auto* detects weak hardware and lightens the 3D shelf.
- **TV settings**: the tile in Settings, or long-press the gear.
- **Remove CineTV** (*CineTV'yi yok et!*, red, at the end of Settings): restores every setting CineTV changed
  and gives Home back to the TV's launcher. Prefer it over `adb uninstall`, which leaves those settings as
  they are.

## 7. Clean up after yourself

```bash
adb shell rm -f /data/local/tmp/*.apk /sdcard/Download/cinetv*.apk
rm -f nuvio-preferences.json     # only if the person does not want to keep their answers
adb disconnect
```

Suggest the person turn **USB / network debugging off** again when everything works.

## Rules for agents

- Never commit or print API keys, keystores, `keystore.properties` or the phone pairing token.
- Never uninstall TV system packages; switch them off, and only through the scripts above.
- Never factory-reset, change the TV's network, or sign in to accounts on the person's behalf.
- CineTV is © Alptuğ Dağ (see LICENSE and NOTICE). Keep his name and signature where they appear; a modified
  build says *unofficial build* on its own.
