# AGENTS.md — installing CineTV for someone, as an AI agent

You are helping a person turn their Android TV / Google TV into CineTV. CineTV is set up **for the person
who watches it**: you get to know them first, then install only what they will use. Someone who never
watches films gets no film rows; someone who only watches Turkish series gets Turkish series; a football
fan gets football. Nothing they would never watch is installed. Everything works from a computer on the
same network as the TV, over `adb`. Nothing needs root.

Never print API keys, never pass them as command-line arguments, never write them to a file in this
repository.

---

## Quick path: follow these steps exactly, in order

Do one step at a time. After each step, check its **Expect** line before going on. If it does not match,
look the problem up in [§9 When something goes wrong](#9-when-something-goes-wrong) and do what it says. Never
skip a step, never invent a command that is not written here, and talk to the person in their language.

| # | Do | Expect |
| :--- | :--- | :--- |
| 1 | Ask the person for the TV's IP address. If they do not know it, read them [KURULUM.md, Adım 1](KURULUM.md#adım-1-televizyonda-geliştirici-seçeneklerini-açın-hepsi-için-gerekli) (developer options, network debugging, IP). | An address like `192.168.1.50` |
| 2 | `adb version` | A version line. If "command not found": macOS `brew install android-platform-tools`; Linux `sudo apt install adb`; Windows: tell the person to run [CineTV-Kur.bat](https://github.com/AlptugDag/cinetv-launcher/releases/latest/download/CineTV-Kur.bat) instead and stop here |
| 3 | `git clone https://github.com/AlptugDag/cinetv-launcher && cd cinetv-launcher` (skip if you are already in it) | The folder has `install.sh` |
| 4 | `adb connect <IP>:5555`, then tell the person: "Look at the TV: tick *Always allow* and press *OK*". Then `adb devices` | `<IP>:5555	device`. `unauthorized`: the TV is still asking, wait. `offline`: `adb kill-server && adb connect <IP>:5555` |
| 5 | Interview the person (§1), write `cinetv-profile.json`, run `python3 scripts/personalize.py --profile cinetv-profile.json --plan` and show them the plan | They say yes |
| 6 | `./install.sh --tv <IP>:5555 --profile cinetv-profile.json --yes` (add the API keys as environment variables if they gave any, §2; add `--debloat` only if they agreed, §5) | Ends with `Done. Press Home on the remote.` |
| 7 | `./install.sh --tv <IP>:5555 --verify` | Every line `✓` (or PASS). `!` lines are optional extras |
| 8 | Finish Nuvio (§3) by screenshots and keys | Their rows on Nuvio's home |
| 9 | Clean up (§8) and tell them the phone remote and YouTube *Send to TV* (§6) | Done |

---

## 0. Before you start

- Tools on the computer: `adb` (Android platform-tools), `curl`, `bash`, `python3`. On macOS:
  `brew install android-platform-tools`.
- On the TV (the person does this with the remote; tell them step by step):
  1. *Settings › Device preferences › About*: press **Build** 7 times ("You are now a developer").
  2. *Settings › Device preferences › Developer options*: turn on **USB debugging** and, if present,
     **Network debugging** / **Wireless debugging**.
  3. Note the IP address in *Settings › Network*.
- Connect: `adb connect <TV_IP>:5555`. The TV shows *Allow USB debugging?*: the person ticks **Always allow**
  and presses **OK**. `adb devices` must then show the TV as `device`, not `unauthorized` or `offline`.
  If it goes `offline` later: `adb kill-server && adb connect <TV_IP>:5555`.

## 1. Get to know the person (the most important step)

The questions are in [`scripts/personalize/questions.json`](scripts/personalize/questions.json): 36 of them,
in six languages, each with its option values and a `when` rule (a question is asked only if an earlier
answer matches). Ask them **in conversation, in the person's language**, starting with the language
question. Rules:

- Ask every question whose `when` matches; skip the others. Do not invent questions, and do not drop any:
  each answer changes the setup (see the table below).
- Offer the options in plain words; store their `v` values. Multi-choice answers are lists. Text answers
  stay text (`teams` is a comma-separated list, `country` is two letters).
- Where the TV already knows something, propose it: `adb shell getprop persist.sys.locale` gives the TV's
  language and country.
- When everything is answered, write `cinetv-profile.json` as `{ "<question id>": <value>, ... }`, then show
  the person the plan and ask them to confirm it:

```bash
python3 scripts/personalize.py --profile cinetv-profile.json --plan
```

A person can also answer in the terminal: `python3 scripts/personalize.py --interview-only` asks the same
questions and writes the same file.

What the answers change:

| Answer | Effect on this TV |
| :--- | :--- |
| `lang` | for Turkish: the Anthology plugins that fit what they watch; the TV's language (its language screen opens if it differs), Nuvio's titles and plots, stream audio order, subtitle default |
| `country`, `city` | weather and the shelf of the moment; which services' catalogues and free channels match; Netflix Top 10 of that country |
| `household`, `kids_ages` | kids' series and family films rows (animated for under-9s) |
| `loves`, `main` | which rows, addons and apps exist at all; `main` comes first and is pinned |
| `audio`, `more_audio`, `anime_audio` | Torrentio's language priority (dubbed, original, Japanese for anime) |
| `subs` | OpenSubtitles (and TR Altyazı for Turkish); Nuvio's subtitle languages |
| `film_*` | film rows by genre, era, style (popular / acclaimed / hidden gems) and country of origin; avoided genres are removed everywhere |
| `series_*` | series rows by genre and origin; `series_only_origins` = nothing from anywhere else, service rows included |
| `anime_kind` | anime series / films / genre rows (Japanese originals) and Anime Kitsu |
| `sports`, `sports_what`, `teams` | sports films, series and documentaries; highlights through Nuvio's YouTube, with the teams' official channels to follow |
| `doc_topics` | documentary rows by topic |
| `channel_kinds`, `channel_countries` | free live channels (iptv-org's public index) of those kinds and countries |
| `services` | the services they pay for: their official apps on Home (Play Store page opened for each one missing) and "On Netflix (in your country)"-style rows; `top10` adds Netflix Top 10 of the country |
| `tv_sources`, `hdmi_port` | the Live TV tile: the TV's tuner (antenna, satellite, cable), the HDMI input of their satellite/cable/IPTV box, or no tile at all |
| `quality`, `hide`, `autoplay` | Torrentio's quality filter; Nuvio's autoplay |
| `phone`, `cast` | AirPlay receiver only for iPhone/Mac homes; Nova Video Player only if they cast from the phone |
| `notes` | kept in `nuvio-preferences.json` for you to act on |

## 2. Install

```bash
git clone https://github.com/AlptugDag/cinetv-launcher && cd cinetv-launcher
CINETV_TMDB_KEY=... CINETV_AI_KEY=... CINETV_MDBLIST_KEY=... \
  ./install.sh --tv <TV_IP> --profile cinetv-profile.json --theme cinetv --yes
```

Add `--debloat` only if the person agreed (§5). Themes: `cinetv` (Alptug Default) · `glass` (Liquid
Glass) · `metal` (Metallic) · `cinema` (Cinema Hall) · `dvdstore` (DVD Store) · `neon` (Neon Night) · `oled`.

**API keys**, all optional and all also enterable later on the TV in *Settings › AI & films*:

| Variable | What it unlocks | Where the person gets it |
| :--- | :--- | :--- |
| `CINETV_TMDB_KEY` (or `CINETV_TMDB_TOKEN`) | localized titles, posters, plots, people | themoviedb.org › Settings › API |
| `CINETV_AI_KEY` | AI search, answers, the mood shelf | any of Gemini (aistudio.google.com), Claude, OpenAI, Grok (xAI), OpenRouter; the key's format picks the provider |
| `CINETV_MDBLIST_KEY` | ratings from IMDb, Rotten Tomatoes, Letterboxd | mdblist.com › Preferences › API |

Keys are handed to the TV once and moved into its hardware-backed encrypted store; they cannot be read back.

The installer then sets up exactly what the plan said: the apps the person needs (and no others), the apps
of the services they pay for (it opens each missing one's Play Store page: press **Install** there), Home's
first row, the Live TV tile's source, Nuvio's addons through its install links, and the person's rows as
`/sdcard/Download/nuvio-collections.json`. Apps it could not install are listed in `nuvio-preferences.json`
under `apps_to_install`: install them from the Play Store page the same way.
It writes `nuvio-preferences.json` with what is left to do in Nuvio's own screens.

## 3. Finish in Nuvio

Some of Nuvio's screens have no link or command; finish them by remote keys. Take a screenshot after each
step and read it before the next key:

```bash
adb exec-out screencap -p > shot.png          # look at the screen
adb shell input keyevent 19|20|21|22          # up | down | left | right
adb shell input keyevent 23                   # OK
adb shell input keyevent 4                    # back
adb shell input text 'https://...'            # type into the focused field
```

Read `nuvio-preferences.json`, open Nuvio (`adb shell monkey -p com.nuvio.tv 1`) and:

1. **Rows** (`collections_file`): *Addons › Collections › Import › From file*. Nuvio reads
   `nuvio-collections.json` from Downloads. The person's rows appear on Nuvio's home, the main interest
   pinned first.
2. **Plugins** (`plugin_repository`, `plugins_to_enable`): *Settings › Plugins › Add repository*, paste the
   URL, then switch on only the plugins listed (chosen from what the person watches). Leave
   `plugins_to_leave_off` off ("Anthology Canlı Spor", "Anthology Canlı TV", "Anthology Sinema
   Kanalları": they carry paid channels); only the person may switch those on, by hand.
3. **Playback**: subtitle languages from `subtitles`, in that order; *autoplay the best source* from
   `autoplay_best_source`.
4. **YouTube** (`youtube_follow`): in Nuvio's YouTube, search each name, open its official channel and add
   it to the library, so highlights and new videos show up.
5. If the installer said the TV is in another language, the TV's language screen is open: pick the
   person's language there.

## 4. Check it

```bash
./install.sh --tv <TV_IP> --verify
```

Every line must say PASS (WARN lines are optional features). Then press **Home** on the remote: CineTV appears.
If the TV's own launcher comes back after a reboot, run the install again; it re-applies Home.

## 5. Safe debloat (what `--debloat` will and will not do)

- Only advertising, telemetry and promo apps the maker pre-installed, chosen by brand
  (`scripts/debloat_universal.sh`: Arçelik/Beko/Grundig, TCL, Sony, Philips, Xiaomi, Hisense…).
- Never the TV core: tuner, HDMI, picture, sound, inputs, CEC, Play services, keyboard, Bluetooth.
- Never an app opened in the last 30 days.
- Apps are **switched off**, not uninstalled; every change goes to `debloat-journal.txt` (and a copy on the TV
  at `/sdcard/cinetv-debloat.txt`). `scripts/restore_all_tv_apps.sh` undoes exactly those.
- Emergency (no picture, no inputs): `scripts/restore_tv_core.sh`.

Tell the person before you run it, in one sentence, and run it only if they agree.

## 6. The phone

Once installed, the phone button at the top of Home shows an address and a QR code. The person opens it on their phone
(same Wi-Fi), presses **Allow** on the TV once, and the phone is remembered. The page is HTTPS
(`https://<ip-with-dashes>.local-ip.sh:9443`) and can be added to the home screen. It has search (Enter shows the
results on the TV too), a full remote that works in every app (keys reach any app through a small bridge
CineTV starts on the TV: the TV asks once to allow it; tell the person to tick "Always allow" and press
Allow), typing from the phone's keyboard, casting of any video link or film page (the TV presses play, skips
the ads and ignores anything under three minutes), a real power-off (tap twice), and a background-photo upload.

**YouTube's "Send to TV"**: CineTV answers on the network as *"<TV name> · YouTube"*. In the YouTube app on any
phone on the same Wi-Fi, the person taps Cast and picks that name; the video opens in TizenTube. If the name
does not appear: TizenTube › Settings › *Link with TV code*, and on the phone YouTube › Settings › *Watch on TV* ›
*Enter TV code*.

## 7. On the TV itself

- **The TV's own settings**: hold the gear on Home (its label says so), *Settings › TV settings*, or the
  remote's own Settings button. Tell the person.
- **Live TV**: on TVs with a tuner, the *Live TV* tile opens the TV's own channels; *Settings › TV channels on
  Home* turns it off.
- **Optimize & test** (in Settings): freezes cached apps, shortens animations, frees memory. Safe to run any time.
- **Performance**: Low, Medium or High, each adapting by itself to what holds the TV back; *Determine
  automatically* runs a short benchmark and picks one. Run it once after installing.
- **Remove CineTV** (*CineTV'yi yok et!*, red, at the end of Settings): restores every setting CineTV changed
  and gives Home back to the TV's launcher. Prefer it over `adb uninstall`, which leaves those settings as
  they are.

## 8. Clean up after yourself

```bash
adb shell rm -f /data/local/tmp/*.apk /sdcard/Download/cinetv*.apk
adb disconnect
```

Keep `cinetv-profile.json` if the person may want to set up another TV the same way. Leave network debugging
**on**: the phone remote's key bridge and CineTV's own repair (it makes itself Home again if something resets
it) use it, and only devices the person allowed on the TV can connect.

## 9. When something goes wrong

| What you see | What to do |
| :--- | :--- |
| `adb: command not found` | macOS `brew install android-platform-tools`; Linux `sudo apt install adb`; Windows: the person runs `CineTV-Kur.bat` (KURULUM.md, Yol B) |
| `failed to connect` / `Connection refused` | Wrong IP, or network debugging off (KURULUM.md, Adım 1). Same Wi-Fi? Ask the person to restart the TV and try again |
| `unauthorized` | The TV is showing *Allow debugging?*. Ask the person to tick *Always allow* and press OK, then `adb devices` again |
| `offline` | `adb kill-server && adb connect <IP>:5555` |
| `more than one device` | Always pass `--tv <IP>:5555` (and `adb -s <IP>:5555 ...` for your own commands) |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | install.sh handles it (it reinstalls). By hand: `adb uninstall com.cinetv.launcher`, then install again |
| `python3: command not found` | macOS: `xcode-select --install`; Linux `sudo apt install python3`. Or `./install.sh --no-personalize --apps --nuvio` |
| `Could not reach GitHub releases` | No internet on the computer, or GitHub rate limit: wait a minute, or download `CineTV.apk` from the releases page and pass `--apk CineTV.apk` |
| Verify says *CineTV is not the Home app* | `adb shell cmd package set-home-activity com.cinetv.launcher/.MainActivity`, then press Home on the remote |
| Phone remote keys do nothing outside CineTV | The TV's *Allow debugging?* for CineTV was not accepted: `adb shell am force-stop com.cinetv.launcher`, press Home, and have the person tick *Always allow* |
| The TV went black after a command | Nothing is broken: ask the person to press Home on the remote. If live TV/HDMI are gone: `scripts/restore_tv_core.sh` |

If a command here fails twice in a row, stop and tell the person what you saw, word for word; never guess.

## Rules for agents

- Never commit or print API keys, keystores, `keystore.properties` or the phone pairing token.
- Install nothing the plan does not list; the point is a TV with only what this person watches.
- Never uninstall TV system packages; switch them off, and only through the scripts above.
- Never factory-reset, change the TV's network, or sign in to accounts on the person's behalf.
- CineTV is © Alptuğ Dağ (see LICENSE and NOTICE). Keep his name and signature where they appear; a modified
  build says *unofficial build* on its own.
