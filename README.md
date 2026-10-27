<div align="center">

<img src="docs/media/wordmark.png" alt="CineTV by Alptuğ Dağ" width="560">

**A cinema-first home screen for any Android TV, Google TV stick or Android projector, set up for whoever
watches it.** The installer gets to know you first, then installs only what you will watch, in your language.
Your films on a shelf of real 3D DVD cases, an AI that understands what you want to watch, your phone as
remote and caster, and a TV tuned the way the film was mastered. One command to install, free.

**[⬇ Download CineTV.apk](https://github.com/AlptugDag/cinetv-launcher/releases/latest/download/CineTV.apk)** · [Kurulum rehberi / Install guide](KURULUM.md)

[Türkçe](#türkçe) · [Made for you](#made-for-whoever-watches-it) · [Install](#install-in-one-command) · [Features](#what-it-does) · [Phone](#your-phone-is-the-remote) · [For AI agents](AGENTS.md)

<img src="docs/media/home-dark.jpg" alt="CineTV home" width="760">

</div>

---

## Türkçe

**CineTV**, her Android TV, Google TV çubuğu ve Android projeksiyon için sinema odaklı bir ana ekran; ve
**izleyen kişiye göre kurulur**. Kurulum önce seni tanır (dil, ülke, sevdiklerin, hiç izlemediklerin, spor,
dizi kökenleri, anime, servisler, kalite, cihazlar), sonra sadece izleyeceklerini kurar: televizyonun dili,
Nuvio eklentileri ve sana özel raflar (ör. sadece Türk dizileri, futbol belgeselleri, Kore dizileri), istersen
ücretsiz canlı kanallar ve sadece ihtiyacın olan uygulamalar. Hiç izlemeyeceğin bir şey boşuna kurulmaz.
Abone olduğun servislerin (Netflix, Disney+, TOD, Exxen…) uygulamaları ana ekrana gelir ama *abone değilsen bile onların
içerikleri açılır*; anten, uydu, kablo ya da HDMI'deki alıcına göre Canlı TV kutucuğu doğru yeri açar (ya da hiç görünmez).
Türkçe profillere Nuvio'da Anthology'nin izlediklerine uyan eklentileri gelir (film ve dizi kaynakları, anime
kaynakları, seçtiği kanal türleri). Paralı kanalları taşıyan eklentileri (*Anthology Canlı Spor*, *Anthology Canlı
TV*, *Anthology Sinema Kanalları*) kurulumda kapalı kalır; bunlar yalnızca elle, *Nuvio › Ayarlar › Eklentiler*
bölümünden açılabilir ve *ücretsiz* seyredilebilir.

**DVD Rafı**: Ana ekranda, gün içinde farklı farklı filmlerin -konum, saat hava durumu gibi ögelere göre- önerildiği dvd kutusunda listelendiği, bir kez basınca kutunun açılıp iç kapağındaki Türkçe film özetinin okunduğu, süresinin ve başrol oyuncularının fotoğraflı pul biçiminde listelendiği, bir kez daha basınca cdnin dönüp ilgili filmin nuvio'da açıldığı bir tasarım benimsedim. (Diziler için de özel logolar ve sloganlar yer alıyor).

**Arama**: "Takeshi Kitano filmleri", "araba temalı filmler", "kızı kaçırılan baba filmleri", dizi aramaları yaptığınızda vitrini doğrudan bunlarla dolduracak ve "Türkiye'nin yüz ölçümü kaçtır?" gibi dilediğiniz soruları cevaplayabilecek; Gemini, Claude, Grok, OpenAI anahtarıyla çalışan, sesli aramayı da destekleyen bir search bar ekledim.

**Ekran koruyucu**: filmlerin sahneleri ve repliklerinden oluşan dinamik bir ekran koruyucu, 35 mm film şeridi geçişiyle.

**Görüntü, ses ve ağ**: Ağ hızınızı ve donanımınızı tespit edip hem nuvio yayın ayarlarınızı hem televizyonunuzun ağ, ses ve görüntü ayarlarını; televizyonunuzun sunabileceği (Dolby, hdr, 4k...) en kusursuz sinema deneyimine göre optimize edecek, var olan youtube uygulamanızı reklamsıza çevirip cihaz çapında reklam engelleyecek düzenlemeler yaptım.

**Telefondan kontrol**: Sadece basit bir qr kod kullanarak, tvniz ile aynı ağa bağlı telefonunuzu kumanda olarak kullanabilir, nuvio aramalarınızı onun içerisinden gerçekleştirip tvde anında oynatabilir, tv arka planınızı değiştirebilir veya web adresleri girerek video cast edebilirsiniz. Gerçek iframe referer ve origin yakalama desteği sayesinde yerli film sitelerindeki videolar doğrudan televizyonda sorunsuz oynatılır. (Ayrıca airplay ve airpods desteği de var)

**Canlı TV**: Tuneri olan televizyonlarda ana ekrandaki Canlı TV kutucuğu televizyonun kendi kanallarını açar; Ayarlar › Ana ekranda TV kanalları ile kapatılır.

**Kaynaklar İsimli**: Kaynak tuşu, HDMI'ye takılı cihazın adıyla ("PlayStation 5", "Digiturk") girişleri listeler.

**Güvenli sadeleştirme & 2 GB Sanal RAM**: Televizyonunuzla birlikte gelen gereksiz tüm servislerden, izleyicilerden, Google Asistan'ın arayüzü bölen pencerelerinden kurtulabilirsiniz (Google Ses Tanıma servisi açık kalarak kumandadan mikrofonla Türkçe arama kusursuz çalışmaya devam eder). Root gerektirmeyen 2 GB sanal RAM / zRAM artışı televizyonu daha da ferahlatır.

Beğenmediğiniz takdirde, ayar menüsündeki **CineTV'yi yok et!** tuşu ile her şeyi geri almanız da mümkün tabii.

Tüm bunların kurulumu sırasında, eğer bir yapay zekaya kurdurursanız size çeşitli sorular sorup tarzınızı analiz edecek ve tüm ayarlarınızı, eklenti ve uzantılarınızı ona göre düzenleyecek.
Örneğin sadece anime izlemek, sadece spor kanalları izlemek ya da asla film görmemek gibi şeyler istiyorsanız, tamamen size göre bir kurulum yapacak (Ben en başından kendime göre kurduğum için bu kısmı tam test etme şansım olmadı)

Film, dizi, canlı tv içeriklerine ek olarak canlı spor kanallarını da otomatik olarak ekliyor çeşitli eklentiler sayesinde ama deaktif bırakıyor etik dışı olduğunu düşündüğünden, onları nuvio ayarlarından manuel olarak etkinleştirebilirsiniz.

***Projede hatalar çıkması muhtemeldir (benim ve ailemin televizyonunda yok) eğer çıkarsa, kullandığınız yapay zeka aracından (antigravity (ÜCRETSİZDİR) veya claude desktop tavsiye ederim) ilgili sorunu çözmesini talep edebilirsiniz.
Kurulum: `./install.sh --tv <TV_IP>` — ya da bir yapay zekâ ajanına "CineTV'yi televizyonuma kur" deyin
([AGENTS.md](AGENTS.md)).

### Kurulum

### Kurulum

Kurulumu iki garantili yoldan biriyle yapabilirsiniz:

1. **Sadece Televizyonla (Önerilen & En Kolayı — Bilgisayar Gerekmez)**: Play Store'dan ücretsiz *Downloader* uygulamasını kurun, adres çubuğuna `github.com/AlptugDag/cinetv-launcher/releases/latest/download/CineTV.apk` yazın, kurun ve açın (ya da flash bellekle atın). İlk açılışta açılan **CineTV Kurulum Sihirbazı**'nda *"Tümünü Otomatik Tamamla"* butonuna basın; ana ekran kilitlenmesi, Alptuğflix medya merkezi, Türkçe film/dizi kaynakları, altyazı eklentileri, gizli Nova Player, kumanda tuş servisi ve 2 GB sanal RAM arka planda kumandanızla tek tıkla eksiksiz tamamlanır.
2. **Bir Yapay Zekâya Kurdurun (Gelişmiş & Kişiselleştirilmiş)** (Claude Desktop, Antigravity, Gemini, Cursor…): *"https://github.com/AlptugDag/cinetv-launcher adresindeki AGENTS.md dosyasını oku ve CineTV'yi televizyonuma kur"* deyin. Size sorular sorarak zevklerinize özel katalog rafları ve kişisel profil oluşturur.

*(Geliştiriciler veya terminal meraklıları için tek satır komut: `git clone https://github.com/AlptugDag/cinetv-launcher && cd cinetv-launcher && ./install.sh`)*

**[📥 CineTV.apk'yı indir](https://github.com/AlptugDag/cinetv-launcher/releases/latest/download/CineTV.apk)** · **[Adım Adım Kurulum Rehberi (KURULUM.md)](KURULUM.md)**

Hangi yolu seçerseniz seçin, televizyonda önce **Geliştirici seçenekleri › USB / Ağ hata ayıklama** açık olmalı (rehberin 1. adımı). İlk açılışta televizyon *"hata ayıklamaya izin verilsin mi?"* diye sorarsa **Her zaman izin ver**'i işaretleyip **Tamam**'a basın.

> **%100 Güvenli ve Geri Alınabilir**: Dilediğiniz an CineTV › Ayarlar menüsünün en altındaki kırmızı **"CineTV'yi yok et!"** butonuna basarak televizyonu hiçbir kalıntı kalmadan, Google Asistan ve orijinal ana ekran dahil adeta hiçbir şey olmamış gibi ilk günkü fabrika haline döndürebilirsiniz.


---


## Made for whoever watches it

No two TVs end up the same. Before installing anything, CineTV gets to know the person who watches,
in their own language (English, Türkçe, Português, Español, Deutsch, Français; more fall back to English):
around 25 questions (36 in all, asked only when they apply), most with an answer ready, asked in the terminal or by an AI agent in conversation.

- **Language and country**: the TV, Nuvio, titles, plots, audio order and subtitles all follow your
  language; catalogues and free channels follow your country.
- **What you love, and what you never watch**: films, series, anime, sports, documentaries, kids, reality,
  live channels, YouTube, music. What you don't pick is not installed at all: no film rows for someone who
  doesn't watch films.
- **Your own rows in Nuvio**, built from your answers: film genres, eras and countries of origin
  (acclaimed, popular or hidden gems), series by genre and origin (Turkish series only? Korean dramas?),
  anime by kind, documentaries by topic, kids' rows by age, and "On Netflix in your country" for the
  services you use.
- **Sports fans**: films, series and documentaries about their sports, and highlights and goals from the
  teams' official channels inside Nuvio.
- **Live channels**: free public channels of the kinds and countries you choose.
- **Turkish-speaking viewers** get the [Anthology](https://github.com/falsisdev/anthology) plugins that fit
  what they watch: film and series sources for film and series fans, anime sources for anime fans, and the
  national, news, documentary, kids' and music channels they chose. Its plugins that carry paid channels
  (*Anthology Canlı Spor*, *Anthology Canlı TV*, *Anthology Sinema Kanalları*) are left off by the
  installer; they can only be switched on by hand, in *Nuvio › Settings › Plugins*.
- **The services you already pay for** (Netflix, Disney+, HBO Max, DAZN, TOD, Exxen, Globoplay…): their
  apps go on Home, and Nuvio shows what's on them in your country.
- **Your antenna, satellite, cable or box**: the Live TV tile opens the TV's own channels, switches straight
  to the HDMI input of your satellite / cable / IPTV box, or isn't there if you don't watch TV channels.
- **Only the apps you need**: AirPlay only in iPhone homes, Nova only if you cast from your phone, YouTube
  only if you watch it.

```bash
python3 scripts/personalize.py --profile cinetv-profile.json --plan   # see what a TV would get
```

The questions and what each answer changes are in [AGENTS.md](AGENTS.md#1-get-to-know-the-person-the-most-important-step).

---

## What it does

### A shelf of films you can almost touch
- **Real DVD keep cases in 3D**: printed covers under shrink-wrap, spines with the title, perspective, light
  from above-left, and a contact shadow that always runs parallel to the case.
- **Browse like a physical shelf**: the focused case comes far forward, its neighbours turn their spines to
  you, the row leans with your movement and settles with a little spring. Cases further away are darker and
  slightly out of focus.
- **Linger on a case** and it turns towards the light, its sleeve flaring as it moves.
- **The room is lit by the film**: the focused film's own colour washes softly over the background, and
  nothing else colours the room.
- **A new shelf every time you switch the TV on**: the AI picks one for the moment, from the hour, weekday,
  your city and the weather ("Dramas for a mild October Sunday"). It changes again when you come
  back after a while.
- Prefer flat posters? *Settings › DVD cases* switches to glowing posters.

<p align="center"><img src="docs/media/themes.jpg" alt="Themes" width="760"><br>
<sub>Liquid Glass · Metallic · Cinema Hall<br>Film Closet · Neon Night · Red Carpet</sub></p>

### Opening a film, in two steps
1. **OK** on a case: it flies off the shelf and opens. Inside the cover, the story is handwritten in felt-tip
   over the inner print, with the title and director in the film's own colour.
2. **OK** again: the disc, printed with the film and the CineTV signature, lifts from the hub, spins up
   and comes towards you until it fills the screen, then the film starts in Nuvio.

| Inside the case | With trailer moments | The disc |
| :---: | :---: | :---: |
| <img src="docs/media/dvd-note.jpg" alt="Handwritten synopsis" width="250"> | <img src="docs/media/dvd-trailer.jpg" alt="Trailer in the tray" width="250"> | <img src="docs/media/disc.jpg" alt="Disc" width="250"> |

### Search that understands you
Type, speak (microphone in the bar or the remote's voice key) or send from your phone:

| You ask | CineTV shows |
| :--- | :--- |
| `Takeshi Kitano films` | his films and series |
| `films about cars` | car films (TMDB keywords + the AI's own picks) |
| `films where a father's daughter is kidnapped` | films with that plot (*Taken*, *Prisoners*, *Man on Fire*…) |
| `series like Breaking Bad` | series like it |
| `Inception` | every title with that name |
| `How big is Turkey?` | a short answer in a bubble under the bar |

It understands any language: the screenshots below are from a Turkish TV.

Works with **any AI key**: Gemini, Claude, Grok, OpenAI or OpenRouter, recognised from the key itself.
Without a key, people, titles and genres still work through TMDB. The on-screen keyboard is CineTV's own,
in glass, with the Turkish Q layout on Turkish TVs.

| Answers | Glass keyboard |
| :---: | :---: |
| <img src="docs/media/answer.jpg" alt="Answer bubble" width="380"> | <img src="docs/media/keyboard.jpg" alt="Keyboard" width="380"> |

### A screensaver made of cinema
Film stills with a line from the film in your language, its official tagline and its **title in the film's
own logo lettering**. Moments change like frames on a **35 mm strip** pulled through the gate: perforations
running along the edges, a little gate weave and flicker. It also takes over when an app such as Nuvio sits
idle with nothing playing, and puts the panel to sleep after a while.

<p align="center"><img src="docs/media/screensaver-film-strip.gif" alt="Screensaver" width="560"></p>

### A TV tuned for films
On first start, and from *Settings › Picture, sound & network tuning*:
- **Picture as mastered**: motion smoothing, noise reduction, dynamic contrast and sharpening off; warm colour
  temperature; film mode; local dimming low (applied directly where the TV allows it, otherwise listed).
- **Sound**: Dolby / Atmos and surround on; auto volume, levellers and "dialogue boosters" off; bitstream to
  an HDMI soundbar or receiver, virtual surround for the TV's own speakers.
- **Network**: ad- and tracker-blocking encrypted DNS (AdGuard, or Cloudflare for speed), a Cloudflare speed
  test, and the best stream quality for your connection and panel. Your original settings can be restored.

### Home, apps and the rest
- App tiles in metallic glass with each app's own colour; all tiles one size, columns grow with the number of
  apps, the focused tile pushes its neighbours aside. **Add** is always after the last app.
- **The TV's own settings are always one step away**: hold the gear on Home (its label says so when it is
  focused), open *Settings › TV settings*, or press the remote's own Settings button.
- **Live TV tile**: on TVs with a tuner, a *Live TV* tile opens the TV's own channels (antenna, cable,
  satellite), whatever the brand. Turn it off in *Settings › TV channels on Home*, or hide it like any app.
- **Inputs by name**: the Input key opens a glass list of the TV's sources named after what is plugged in
  ("PlayStation 5", "Digiturk" over HDMI-CEC, or the label you gave the port), with Live TV first and the
  connected ones marked.
- **Sleep timer and quiet hours**: *Settings › Sleep & quiet hours* puts the TV to sleep in 15 to 120 minutes
  (a minute's warning first), and quiet hours keep the volume at 30 percent or less from 22:00 to 08:00.
- **Long press** (or Menu) on an app: a glass bar under the tile to move, hide, uninstall or open its info.
- **All apps** drawer in the same tiles, without system services.
- **Voice commands and voice search** (on TVs with speech recognition, from the remote's microphone or the
  phone's): "open Inception", "list Nolan's films", a question, or a command: "volume down", "set the volume
  to 15", "channel 5", "HDMI 2", "open YouTube", "sleep in 30 minutes", "turn the TV off", in Turkish or English.
- **Trailer moments** (*Settings*, off by default): an opened DVD turns sideways and plays the film's trailer
  in its tray, muted, a few seconds at a time from several points, with the story across the other side.
- **A tour on first start** shows every highlight where it lives on Home; *Settings › Tour of CineTV* repeats it.
- **Themes, each a different room**: Alptug Default; Liquid Glass (clear glass drops over a cool scene);
  Metallic (brushed titanium, chrome edges); Cinema Hall (a real art-deco cinema, its velvet curtains in front of the
  shelf's ends, the cases in its warm light); Film Closet (a collector's closet of white shelves packed with films, the
  row of cases one of its shelves); Neon Night (synthwave sun and grid);
  Red Carpet (gala premiere with plush red velvet, gold rope trim, and stanchions). Panels and buttons take the room's material. Drawn by the app, apart from the photo backdrops. Or **your own background** sent from your phone. Always dark, as a cinema is.
- **Performance: Low, Medium, High, each adapting by itself** to what holds the TV back (graphics, memory,
  processor), measured again while it runs; *Determine automatically* runs a short benchmark and picks one.
- **Remote buttons**: Netflix opens Nuvio, YouTube opens ad-free YouTube, Prime opens phone casting; any
  button can be given any job.
- **AirPods** (and other paired headphones) reconnect by themselves when you open the case. AirPlay and
  Chromecast receivers stay available in the background.
- **Performance** adapts to the TV (light / balanced / powerful) and steps down by itself if frames drop;
  *Optimize* frees memory, tunes the system and measures the TV. Apps close fully a minute after you leave
  them.
- **Your signature everywhere**: the CineTV mark is Alptuğ Dağ's signature; it opens the About page.
- **CineTV'yi yok et!** at the end of Settings puts back every setting CineTV changed and removes it.

| Settings | First-run tour | Long press |
| :---: | :---: | :---: |
| <img src="docs/media/settings.jpg" alt="Settings" width="250"> | <img src="docs/media/tour.jpg" alt="Tour" width="250"> | <img src="docs/media/long-press.jpg" alt="Long-press bar" width="250"> |

---

## Your phone is the remote

Open the address CineTV shows (or scan its QR code) on any phone on the same Wi-Fi, and allow it once on the
TV. From then on it connects by itself.

- **Secure page, no "not secure" warning**: the TV serves it over HTTPS (a real certificate for
  `<ip-with-dashes>.local-ip.sh`), and the plain address forwards there by itself.
- **Installable**: an *Add to Home Screen* prompt turns it into an app icon.
- **Discover**: trending titles; type to search, press Enter and the TV's shelf shows the same.
- **A full TV remote that works in every app** (Nuvio, live TV, the TV's own menus), on one screen in liquid
  glass: a touchpad (swipe to move, tap for OK, hold for a long OK), volume and channel rockers, keyboard,
  home, numbers, mute, voice and back; swipe the pad for input, guide, menu, info, media, subtitles, audio,
  colour keys, sleep timer and screensaver. **Typing from the phone's keyboard** goes into whatever field is
  selected on the TV, Turkish letters included. Double taps never zoom the page. **Power** (tap twice)
  really switches the TV off; its own remote turns it on again. Keys reach any app through a small bridge
  CineTV starts on the TV itself; the TV asks once to allow it ("Always allow").
- **Cast by browsing**: open a film page from the phone; the page appears on the phone, live, and you tap it
  as you would any page: skip the site's ads, press the film's play. The film's stream (three minutes or
  more, so ads are never sent) then plays on the TV. The page itself stays silent on the TV.
- **Air mouse**: switch it on on the touchpad and point the phone (or slide a finger): a pointer moves over any
  app on the TV, a tap clicks, two fingers scroll.
- **Voice from the phone**: the microphone key takes the same commands and searches as the TV's remote.
- **YouTube's "Send to TV"**: in the YouTube app on any phone on the same Wi-Fi, tap Cast and pick
  *"<your TV> · YouTube"*: the video opens in ad-free TizenTube, and the phone controls it as usual.
- **Background**: pick a photo and it becomes the backdrop of Home.
- **Several CineTV TVs** on one network show up in a switcher.

<p align="center"><img src="docs/media/phone-strip.jpg" alt="Phone remote, more keys, air mouse, casting" width="720"></p>

---

## Install in one command

Five ways, from "only the TV" to "an AI does it", are in the step-by-step [install guide](KURULUM.md). The
most complete one, on a computer on the same network as the TV (macOS, Linux, or Windows with WSL), with
`adb` and `python3` installed:

1. On the TV: *Settings › Device preferences › About*, press *Build* 7 times, then *Developer options ›
   Network debugging* on. Note the TV's IP address.
2. Run:

```bash
git clone https://github.com/AlptugDag/cinetv-launcher && cd cinetv-launcher && ./install.sh --tv 192.168.1.50
```

The installer finds the TV, downloads the latest signed release (checksum verified), **gets to know you**
([above](#made-for-whoever-watches-it)) and sets the TV up around your answers: CineTV as Home, the
remote-button service, the screensaver, your language, only the apps you need (**Nuvio**, ad-free
**YouTube**, **Nova Video Player**, the **AirPlay** receiver), Nuvio's addons and your own rows, and, if you
want it, a **safe debloat**. Answers are saved in `cinetv-profile.json`, so a second TV can be set up the
same way with `--profile`. API keys are optional:

```bash
CINETV_TMDB_KEY=... CINETV_AI_KEY=... CINETV_MDBLIST_KEY=... ./install.sh --tv 192.168.1.50
```

Keys go to the TV once and are moved into its encrypted keystore; they are never shown again or sent anywhere
but their own service. You can also type them on the TV in *Settings › AI & films*.

**With an AI agent** (Claude Code, Cursor, Codex…): point it at this repository and say *"install CineTV on my
TV at 192.168.1.50"*. [AGENTS.md](AGENTS.md) tells it how to interview you, what to run and how to finish
Nuvio's screens.

### Safe debloat
`--debloat` switches off advertising, telemetry and promo apps the TV maker pre-installed, and only those:
- never the TV core (tuner, HDMI, picture, sound, inputs, CEC, Play services, keyboard, Bluetooth);
- never an app you opened in the last 30 days;
- switched off rather than removed, so each one comes back with one click in *Settings › Apps*, and a factory
  reset restores everything: no warranty is touched;
- every change is written to a journal, and `scripts/restore_all_tv_apps.sh` undoes exactly those.

### Scripts
| Script | What it does |
| :--- | :--- |
| `install.sh` | everything above; `--verify` checks an installation |
| `scripts/personalize.py` | the interview and the setup it leads to; `--plan` shows it without changing anything |
| `scripts/setup_apps.sh` | Nuvio, TizenTube, Nova, AirPlay from their official releases |
| `scripts/setup_nuvio.sh` | the short Nuvio questionnaire, for installs without the interview |
| `scripts/debloat_universal.sh` | brand-aware safe debloat (Arçelik/Beko/Grundig, TCL, Sony, Philips, Xiaomi, Hisense…) |
| `scripts/restore_all_tv_apps.sh` | undoes the debloat and re-enables stock apps |
| `scripts/restore_tv_core.sh` | emergency: re-enables the TV's own core packages |
| `scripts/verify_display.sh` | checks the display plane, HDR and 4K playback |

---

## Works on

Android TV / Google TV 8.0 and newer: TVs (Arçelik, Beko, Grundig, TCL, Sony, Philips, Xiaomi, Hisense,
Toshiba…), sticks and boxes (Chromecast with Google TV, Mi Box, Nvidia Shield…), and Android
projectors. Weak hardware is detected and served a lighter profile: 1 GB TVs keep the 3D shelf. Tested on
Android 11 (Arçelik) and Android 14 Google TV (Grundig), including the phone remote's key bridge and each
brand's own live TV app.

---

## Privacy and security

- No analytics, no tracking, no crash reporting. Calls go only to TMDB, Cinemeta, the AI you chose, the
  weather service, Google Translate for a synopsis TMDB has only in English, the pages you cast and, for
  HTTPS on the phone page, local-ip.sh's public certificate.
- API keys live in an AES-256-GCM store whose key never leaves the TV's hardware keystore.
- The phone server answers only phones you allowed on the TV, refuses other host names (DNS-rebinding
  guard), and will not fetch addresses on your home network for casting.
- Official builds are signed with the author's key; a changed or repackaged copy says *unofficial build*.

---

## Building from source

```bash
./gradlew :launcher:assembleDebug          # debug build
./gradlew :launcher:testDebugUnitTest      # unit tests
```
Release builds are signed only with the release key (see `scripts/release/` and the GitHub release workflow)
and are shrunk and obfuscated with R8.

Code layout (`launcher/src/com/cinetv/launcher`): `ui/` what is drawn (shelf, cases, screensaver, panels,
keyboard), `service/` apps, films, themes, secrets, `ai/` search and curation, `remote/` the phone page
server, casting and HTTPS, `device/` keys, picture and sound, network, memory, AirPods, performance.

---

<div align="center">

Made by **Alptuğ Dağ** — filmmaker, storyteller ·
[Instagram](https://instagram.com/alptugdag) · [YouTube](https://youtube.com/@AlptugDag) ·
[Letterboxd](https://letterboxd.com/AlptugDag/) · [LinkedIn](https://www.linkedin.com/in/AlptugDag/) · [GitHub](https://github.com/AlptugDag) ·
[TMDB](https://www.themoviedb.org/person/6564105) · [IMDb](https://www.imdb.com/name/nm14913967)

Source-available under the [PolyForm Strict License 1.0.0](LICENSE): read it, build it for yourself and use it
for free, but do not publish changed copies, forks or launchers based on it, or remove its author's name and
signature. *Kaynak kod açık ve incelenebilir; değiştirilip yeniden yayımlanamaz, başka bir uygulamaya
dönüştürülemez.* Third-party credits are in [NOTICE](NOTICE).

</div>
