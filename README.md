# 📺 CİNETV Launcher — Ultimate Cinema Operating Suite

[![Release](https://img.shields.io/badge/Release-v1.0.0-blue.svg)](release/tv-master-launcher-v1.0.0.apk)
[![Platform](https://img.shields.io/badge/Platform-Android%20TV%209%2B%20%7C%20Google%20TV%20%7C%20Projectors-green.svg)](#supported-devices)
[![Architecture](https://img.shields.io/badge/Arch-armeabi--v7a%20%7C%20arm64--v8a-orange.svg)](#architecture)
[![Based on](https://img.shields.io/badge/Origin-CleanYourTV-blueviolet.svg)](https://github.com/Gathin23/cleanyourtv)
[![License](https://img.shields.io/badge/License-Apache%202.0-yellow.svg)](LICENSE)

<p align="center">
  <a href="#-topics--tags"><code>#android-tv</code></a> •
  <a href="#-topics--tags"><code>#google-tv</code></a> •
  <a href="#-topics--tags"><code>#projectors</code></a> •
  <a href="#-topics--tags"><code>#tv-launcher</code></a> •
  <a href="#-topics--tags"><code>#nuvio</code></a> •
  <a href="#-topics--tags"><code>#cinema-station</code></a> •
  <a href="#-topics--tags"><code>#gemini-ai</code></a> •
  <a href="#-topics--tags"><code>#tmdb-cinema</code></a> •
  <a href="#-topics--tags"><code>#web-remote</code></a> •
  <a href="#-topics--tags"><code>#4k-hdr10</code></a> •
  <a href="#-topics--tags"><code>#debloat</code></a>
</p>

> **"Sadece televizyonunuzu ve projeksiyonunuzu temizlemekle kalmıyor; onu tek tıkla doğrudan film izleyebileceğiniz, yapay zekâ küratörlü, telefon kumandalı eksiksiz bir 4K HDR sinema üssüne çeviriyoruz."**  
> **"We don't just clean up your TV or Projector — we transform it into a dedicated, AI-curated, one-click 4K HDR cinema powerhouse with zero bloat and seamless phone remote control."**  
>
> Features a stunning **Liquid Glass 3D Parallax CoverFlow** vitrin across **dynamic cinema & series categories**, **Multi-LLM Autonomous AI Curator (Google Gemini & Anthropic Claude)** driven by time, weather & mood, **Live TMDB Backdrop Screensaver** with cinematic philosophy quotes, **Integrated Search & Gemini Chat HUD Bar**, **Phone Web Remote (Port 8080)**, **One-Click Nuvio TV + Anthology + TurkSinema** 4K HDR10/Dolby Vision streaming suite, **Hardware Remote Key Interceptor (Netflix, YouTube, Prime, Live TV)**, **Leanback Reorder / Uninstall Dock Management with `+` Card**, **Real-Time Live Weather**, **AdGuard Private DNS over TLS (DoT)**, **Native AirPlay receiver**, universal multi-brand debloating, and OLED/Laser burn-in safe drifting starfield screensaver.

---

### 🏷️ Topics & Tags
`android-tv` `google-tv` `projectors` `smart-projector` `tv-launcher` `cinema-suite` `nuvio` `nuvio-tv` `anthology` `turksinema` `androidtv-launcher` `custom-launcher` `leanback` `gemini-ai` `tmdb-api` `web-remote` `4k-hdr10` `dolby-vision` `debloat-android-tv` `smart-tv` `coverflow`

---

> [!NOTE]
> **Attribution & Origin**: This project is built upon the wonderful foundation of [**CleanYourTV**](https://github.com/Gathin23/cleanyourtv) by Gathin23. We have re-engineered and expanded it into **CİNETV / TV Master Launcher v1.0.0**, introducing hardware Z-order 3D CoverFlow rendering, dynamic 12-bin focal neon glow extraction, Nuvio & Anthology direct 4K playback integration (click any poster to stream instantly), autonomous Gemini/Claude mood & context curation, TMDB media backdrop integration, full remote control hardware remapping, phone web remote server, AdGuard DoT security, universal projector & TV support, an interactive setup wizard, and strict user privacy.

---

## 🌟 Canlı Görsel & Video Vitrini (Visual Showcase)

### 🎬 Canlı Kullanım ve Akıcı Arayüz Deneyimi (1080p Screen Record)
Televizyon üzerinden doğrudan kaydedilen akıcı 60 FPS D-pad gezinmesi, dinamik odak derinliği ve 3D CoverFlow vitrin geçişleri:

https://github.com/AlptugDag/tv-master-launcher/raw/main/docs/screenshots/cinetv_showcase.mp4

> *Tarayıcınızda doğrudan video oynatıcı açılmazsa veya mobil önizlemedeyseniz:*  
> Akıcı animasyon önizlemesi için `docs/screenshots/liquid_glass_glow.gif` dosyasına göz atabilirsiniz.

<p align="center">
  <img src="docs/screenshots/liquid_glass_glow.gif" alt="CineTV Liquid Glass Motion" width="100%" style="border-radius: 12px; box-shadow: 0 10px 30px rgba(0,0,0,0.8);" />
</p>

---

### 📸 Güncel Arayüz Galerisi (Gerçek TV Ekran Yakalamaları)

| 🎴 Takeshi Kitano Başyapıtları CoverFlow Vitrini | 🔍 Search Bar Metni & Gemini Floating AI HUD Kartı |
| :---: | :---: |
| ![Hero CoverFlow](docs/screenshots/hero_coverflow_3d.png) | ![Cinema HUD Overlay](docs/screenshots/cinema_hud_overlay.png) |
| **📱 Tam Ekran Sayfa 2: visionOS Uygulama Kütüphanesi (5 Sütun)** | **🌌 TMDB Sahne Arka Planlı & Saydam Logolu Screensaver** |
| ![Page 2 Library](docs/screenshots/dock_nuvio_showcase.png) | ![Modern Screensaver](docs/screenshots/modern_screensaver.png) |

---

### 🕹️ D-pad Uzun Basma Liquid Glass Aksiyon Barı (Canlı Önizleme)
Dock üzerindeki herhangi bir uygulamaya kumandadan uzun basıldığında çıkan akıcı visionOS aksiyon hapları (`↔️ Taşı`, `👁️ Gizle`, `🗑️ Kaldır`, `ℹ️ Bilgi`):

<p align="center">
  <img src="docs/screenshots/dpad_actions.gif" alt="D-pad Action Pill Bar Motion" width="80%" style="border-radius: 14px; box-shadow: 0 10px 30px rgba(0,0,0,0.85);" />
</p>

---

### 🎨 12-Bin Focal Neon Kromatik Bloom (Poster-Aware Ambient Glow)
Her odaklanan afişin sanatsal renk paletini 12 renk kutusunda analiz ederek ten tonlarını filtreler ve afişe özel canlı neon parıltısı üretir:

| Crimson Neon | Warm Amber Neon | Electric Cyan Neon |
| :---: | :---: | :---: |
| ![Crimson Neon](docs/screenshots/focal_neon_crimson.png) | ![Warm Amber Neon](docs/screenshots/focal_neon_amber.png) | ![Electric Cyan Neon](docs/screenshots/focal_neon_cyan.png) |

---

## ⚡ Temel Yetenekler & Mimari (Key Highlights & Architecture)

### 1. 🎴 Liquid Glass 3D Parallax CoverFlow Vitrin & Saf Afiş Estetiği
- **Hardware Z-Ordering**: Android RenderNode `elevation` (`50f`) ve `translationZ` (`65f`) donanım katmanlarını kullanarak ViewGroup hiyerarşisini bozmadan akıcı 60 FPS gezinme sağlar.
- **Dinamik Derinlik Ölçekleme**: Odaklanan afiş **1.30x** büyür ve `-22f` yukarı kalkar; komşu kartlar mesafeye göre **0.94x**, **0.80x** ve **0.68x** oranında fiziksel gölge ve mesafe sönümlemesiyle geriye çekilir.
- **Saf ve Net Afiş Tasarımı**: Afişlerin üzerindeki dikkat dağıtıcı ve çiğ format etiketleri ("4K HDR10") kaldırılmış; afişin orijinal sinematik sanatı ön plana çıkarılmıştır.
- **Kırpılmayan Neon Parıltısı**: Odaklanma sırasında sol kartın ve logonun etrafındaki yumuşak ışık huzmeleri (`setClipChildren(false)` ve 80px iç dolgu ile) ekranda asla kesilmeden yayılır.
- **Sıfır Gri Flaş**: Uygulamalardan çıkıp ana ekrana dönüldüğünde yaşanan gri ekran flaşı tamamen engellenmiş, saf OLED siyahına (`#040508`) sabitlenmiştir.
- **Tek Tıkla Doğrudan 4K Oynatma**: Vitrindeki herhangi bir afişe tıklandığında anında **Nuvio TV** (`launchMode="stream"`) üzerinden en yüksek seeder'lı 4K HDR10 / Dolby Vision akış başlatılır.

---

### 2. 🤖 Otonom Yapay Zekâ Sinema Küratörü & Filmografi Motoru (Google Gemini & Anthropic Claude)
- **Tarih, Saat, Hava Durumu ve Ruh Hali Odaklı Otomasyon**: Vitrin oluşturmak için manuel istek yapmanıza gerek kalmaz. Launcher; anlık hava durumu (örneğin yağmurlu bir pazar akşamı), saat dilimi ve güncel tarihten yola çıkarak tematik sinema seçkileri hazırlar.
- **TMDB + Gemini Filmografi & Özel Kategori Arama Motoru**:
  - *"Takeshi Kitano filmleri"*, *"Zeki Demirkubuz sineması"*, *"Nuri Bilge Ceylan başyapıtları"* veya *"Cannes En İyi Senaryo"* gibi aramalarda rastgele tek bir film açmak yerine; TMDB & Gemini API entegrasyonuyla sanatçının veya temanın en popüler 10 filmlik tam filmografisi vitrine dinamik olarak yüklenir. Kullanıcı tüm filmografiyi 3D CoverFlow ile gezip dilediğini seçer.
- **Atomik Senkronizasyon (Atomic Crossfade Vitrin)**:
  - Vitrin güncellenirken başlık ile afişlerin uyumsuz kalması önlendi. Başlık ve afişler eşzamanlı olarak, donanım hızlandırmalı pürüzsüz crossfade geçişiyle atomik olarak güncellenir.
- **Keskin Ayrılmış Tematik Kategoriler**: Dram ile komedi gibi zıt türler asla aynı vitrinde harmanlanmaz. Seçkiler keskin ve sofistike temalara ayrılır:
  - **Festivaller & Başyapıtlar**: Cannes Altın Palmiye, Venedik Altın Aslan, Oscar Ödüllü Yönetmen Başyapıtları.
  - **Ülke Sinemaları**: Fransız Yeni Dalgası, İskandinav Karanlık Polisiyeleri, Güney Kore Gizem & İntikam Sineması, İtalyan Neo-Realizm.
  - **Spesifik Yönetmen & Oyuncu Odakları**: Christopher Nolan akıl oyunları, Denis Villeneuve felsefi bilimkurguları, David Fincher gerilimleri.
  - **Ruh Hali & Atmosfer**: "Yağmurlu Gece Melankolisi", "Yüksek Nabızlı Siberpunk Maceraları", "Düşündüren Distopyalar".
- **Dinamik İçerik Yenileme & TMDB Çözümlemesi**: Başlık güncellendiğinde tüm filmler TMDB üzerinden dinamik olarak aranır, gerçek 4K afiş (`w500`) ve sinematik backdrop (`w1280`) görselleriyle vitrine yüklenir.
- **Estetik ve Sade Başlıklar**: Çiğ duran "Öne Çıkanlar • Nuvio" gibi uzantılar kaldırılmış; doğrudan sanatsal ana başlık sunulur.
- **Gizlilik ve Güvenlik**: Gemini (`AIzaSy...`) veya Claude (`sk-ant-...`) API anahtarınız GitHub kodlarında veya repoda ASLA tutulmaz. Kurulum sihirbazında istenir ve doğrudan TV donanımının güvenli kasasına (`Settings.Secure`) kaydedilir.

---

### 3. 🔍 Üst Bar Gemini Arama, Sohbet & Hızlı HUD Kontrolleri
- **Doğrudan Çubuğa Yazılan Inline Arama**: Ayrı bir sistem klavye penceresi veya pop-up açılmaz; D-pad ile odaklanıldığında doğrudan çubuğun içine klavyeyle yazılır (`EditText`).
- **Floating AI HUD Yanıt Kartı**: Gemini'ye yöneltilen her türlü soru (teknik, felsefi, günlük) standart Android AlertDialog yerine ekran üzerinde temayla uyumlu şık bir Liquid Glass Floating HUD kartı (`showAiResponseFloatingCard`) olarak belirir; 10 saniye sonra veya Back tuşuna basıldığında zarifçe kaybolur.
- **Entegre Film Arama**: Film, dizi veya oyuncu arandığında doğrudan Nuvio ve TMDB filmografi motorunda arama yapar ve tek tıkla vitrine veya oynatıcıya yönlendirir.
- **Hızlı Erişim Donanım Düğmeleri**:
  - **Ağ (Network)**: Wi-Fi ve Ethernet bağlantı durumunu görüntüler ve hızlı ağ ayarlarını açar.
  - **Girişler (Inputs)**: HDMI 1, HDMI 2, HDMI 3 ve AV harici girişlerini tek tıkla listeler ve geçiş yaptırır.
  - **Tüm Uygulamalar (All Apps)**: Cihazda kurulu tüm uygulamaları şık cam çekmecede açar.
  - **Hızlı Ayarlar (Gear)**: 4 seçenekli visionOS sistem modalını açar.

---

### 3b. 🪟 visionOS Liquid Glass Özel Menü Suite (Sıfır Varsayılan Android UI)
- **4 Seçenekli Logo & Sistem Ayarlar Modalı**: Ekranda logoya veya dişli çarka tıklandığında açılan, saf OLED siyahı ve neon çerçeveli şık visionOS kartı. Yalnızca 4 temel seçenek barındırır:
  1. *Tema Modu (OLED Siyahı / Buzlu Cam)*
  2. *Şehir Ayarlama (Canlı hava durumu konumu)*
  3. *Televizyonu Yeniden Başlatma (Hızlı reboot)*
  4. *Yapay Zekâ & TMDB API Anahtarı Tanımlama / Değiştirme*
- **Tam Ekran Cam Çekmece (All Apps Drawer)**: Cihazda kurulu sistem ve kullanıcı uygulamalarının GERÇEKTEN TÜMÜNÜ listeleyen, ana sayfa kartlarıyla birebir aynı cam dokuya sahip akıcı tam ekran çekmece (`allAppsOverlay`).
- **Yatay Liquid Glass Aksiyon Hapları (Action Pill Bar)**: Dock'taki bir uygulamaya basılı tutulduğunda eski gri Android kutuları yerine ekranın altında beliren yatay visionOS cam hapları (`↔️ Taşı`, `👁️ Gizle`, `🗑️ Kaldır`, `ℹ️ Bilgi`).

---

### 4. 🎬 TMDB API Entegreli Sinematik Ekran Koruyucu
- **Sıfır Depolama Tüketimi (Dinamik Medya Çekme)**: Ekran koruyucu arka planları TV dahili belleğine indirilip cihazı yormaz; TMDB API üzerinden sinema tarihinin kült yapımlarının `media > backgrounds` (backdrop) galerisinden anlık çekilir.
- **35+ Rastgele Replik & Film Havuzu (Random Shuffle)**: Sürekli aynı filmleri ve aynı sırayla göstermez; her açılışta kült sinema yapımları ve aforizmaları rastgele karıştırılarak sunulur.
- **Merkezi Sabit Kompakt Logo & PorterDuff.Mode.SCREEN**: Ortalanmış kompakt logo (%21), arkadaki TMDB sinema sahnesinin renk ve ışığıyla harmanlanarak ekranla bütünleşir.
- **Büyük ve Okunaklı Felsefi Replikler**: 36f boyutunda, yumuşak gölgeli ve yüksek okunaklı tipografiyle sinema tarihinin kült replikleri ve felsefi aforizmaları ekranda süzülür (*Blade Runner, Interstellar, 2001: A Space Odyssey, Andrei Tarkovsky, Inception, Matrix*).
- **5 Dk Boşta Kalma -> Screensaver -> 10 Dk Sonra Tam Ekran Kapanma**: Cihaz 5 dakika eylemsiz kaldığında sinematik ekran koruyucu devreye girer. Ekran koruyucu 10 dakika boyunca aktif kaldıktan sonra TV ekranını tamamen kapatarak (toplam 15 dakika uyku zamanlaması) panel ömrünü ve enerjiyi maksimum korur.
- **Lissajous Panel Koruması**: Piksel yanmasını (burn-in) önlemek için metinler ve arka planlar sürekli organik bir Lissajous eğrisi üzerinde mikroskopik olarak hareket eder.

---

### 5. 📲 Telefondan Web Kumandası (Çift Ekran Deneyimi — Port 8080)
- **Dahili Web Remote Sunucusu**: Launcher içinde çalışan ultra hafif HTTP sunucusu (`TvWebRemoteServer`) port 8080 üzerinden yayın yapar.
- **Uygulama İndirmeye Gerek Yok**: Aynı Wi-Fi ağındaki herhangi bir telefondan, tabletten veya bilgisayardan `http://<TV_IP>:8080` adresine girerek TV'yi kontrol edin:
  - **Klavye Girişi**: TV kumandasıyla tek tek harf seçmek yerine telefondan hızlıca film adı yazma ve Gemini'ye soru iletme.
  - **D-Pad & Yön Tuşları**: Yukarı, Aşağı, Sol, Sağ, Seç (OK), Geri, Ana Sayfa, Ses Açma/Kısma.
  - **Hızlı Kısayollar**: Nuvio TV, YouTube, Ayarlar ve HDMI girişlerini telefondan tek dokunuşla başlatma.

---

### 6. 🎮 Donanım Kumanda Özel Tuş Atamaları & Erişilebilirlik Servisi
- **Kusursuz Tuş Yakalama**: Televizyon kumandasındaki özel donanım tuşları (`TvKeyInterceptorService`) arka plan servisiyle sisteme entegre edilmiştir:
  - **Netflix Tuşu (`207`)**: Netflix veya Nuvio sinema moduna anında yönlendirme.
  - **YouTube Tuşu (`218`)**: Reklamsız akıllı oynatıcıyı anında tetikler.
  - **Prime Video Tuşu (`185, 209, 228, 192`)**: Doğrudan video merkezine yönlendirme.
  - **Canlı TV / TV+ Tuşu (`277, 170`)**: Canlı TV / DVB-S2 uydu yayınını veya Anthology TV akışını başlatır.

---

### 7. 🎛️ Leanback Dock Yönetimi & `+` Kartı
- **`+` Uygulama Ekleme Kartı**: Dock sırasının en sağına eklenen şık visionOS cam kartı ile cihazdaki yüklü uygulamalar arasından tek tıkla dock'a yeni uygulama ekleyin.
- **Profesyonel Uzun Basma Menüsü (Leanback Context Menu)**:
  - **Konumu Değiştir**: Uygulama kartını D-pad sol/sağ tuşlarıyla dock üzerinde dilediğiniz sıraya taşıyın.
  - **Ana Ekrandan Kaldır**: Dock'u sadeleştirmek için uygulamayı ana ekrandan gizleyin.
  - **Cihazdan Tamamen Sil (Uninstall)**: İstenmeyen uygulamaları doğrudan TV arayüzünden sistemden silin.
  - **Uygulama Bilgisi**: Android sistem uygulama detaylarına gidin.
- **Hayalet Uygulama Önleme (Chrome Bug Çözümü)**: Cihazdan silinen bir uygulama ana ekranda asılı kalmaz; dinamik paket denetleyicisi ve `BroadcastReceiver` sayesinde kaldırılan uygulamalar dock'tan anında temizlenir.

---

### 8. 🍿 Nuvio TV & 4K HDR10 Sinema Ekosistemi (Doğrulanmış Depolar & Eklentiler)
- **Doğrulanmış Nuvio TV Entegrasyonu**: Nuvio TV (`com.nuvio.tv`) ve Apple visionOS liquid glass dock simgesi.
- **18 Dinamik Kategori & Canlı Yayın**: Vizyondakiler, Popüler Filmler, IMDb En İyiler, Bilim-Kurgu Başyapıtları, Gerilim & Gizem, Suç & Polisiye, Animasyon & Aile, Belgesel & Doğa vb.

#### 📦 Doğrulanmış Eklenti, Scraper ve Katalog Ekosistemi

| Eklenti / Depo | Tür | Bağlantı / Manifest | Açıklama & Kapsam |
|---|:---:|---|---|
| **Anthology** | Film, Dizi & Canlı TV | [GitHub Deposu](https://github.com/falsisdev/anthology)<br>`https://raw.githubusercontent.com/falsisdev/anthology/main/manifest.json` | **41 aktif sağlayıcı**, 138 canlı TV kanalı ve 7 vitrin kataloğu. Nuvio Ayarlar -> Pluginler -> Depo Ekle. |
| **TurkSinema** | Film, Dizi, Anime & Belgesel | [GitHub Deposu](https://github.com/Wiojelt/TurkSinema)<br>*(Kısa kod: `!turksinema`)* | CloudStream & Nuvio için **56 ayrı film, dizi, anime ve belgesel** scraper sağlayıcısı. |
| **Torrentio 4K HDR10** | P2P / Debrid Akış Motoru | [Torrentio Yapılandırma](https://torrentio.strem.fun/configure) | 4K REMUX, HDR10 ve Dolby Vision yayınları seeder sayısına ve Türkçe ses/altyazı önceliğine göre sıralar. |
| **AIOMetadata Türkçe** | Gelişmiş Metadata & Afişler | [Gist Yapılandırması](https://gist.github.com/underd0se/0a623550205661139ee73564051f7e80#file-aiometadata-turkce-config-json) | TMDB, TVDB ve Trakt veritabanlarından zengin Türkçe afiş, özet, oyuncu kadrosu ve yaş sınırı bilgileri. |
| **Kaptain's Collection** | Özel Stüdyo & Sinema Sanatı | [kaptain.stream](https://kaptain.stream) | Nuvio, Stremio ve Plex için 4K özel stüdyo logoları, seri afişleri ve yüksek kaliteli koleksiyon görselleri. |
| **TR Altyazı** | Türkçe Altyazı Havuzu | [Manifest URL](https://tr-altyazi.altyazi.workers.dev/manifest.json)<br>*(Tek Tık: `stremio://tr-altyazi.altyazi.workers.dev/manifest.json`)* | AltyaziTR (turkcealtyazi.org), YIFY ve OpenSubtitles ortak havuzundan anında Türkçe altyazı senkronizasyonu. |
| **Cinemeta Global** | Resmi Box Office Katalogları | `https://v3-cinemeta.strem.io/manifest.json` | Dünya genelinde vizyondaki ve trend olan yapımların anlık resmi katalog akışı. |

- **Torrent Sıralama & Filtreleme Hiyerarşisi**:
  1. Kalite Katmanı: `4K HDR10 / Dolby Vision` > `1080p HDR10` > `1080p` > `720p`.
  2. Dil Önceliği: Kullanıcının seçtiği dublaj ve altyazı dilleri (`turkish,english`, `german,english`, vb.).
  3. **Seeder Önceliği**: Torrentler önce seeder sayısına göre, ardından dosya boyutuna göre sıralanır (`sort=quality`).
  4. Düşük formatlar (480p, CAM, SCR) tamamen elenir.
- **Nova Video Player Entegrasyonu (`scripts/setup_nova_player.sh`)**: Ağır 4K REMUX veya nadir ses kodeklerinde Nuvio ayarlarından harici oynatıcı olarak Nova Video Player (`org.courville.nova`) seçilebilir.
- **Donanım Sinema Kalibrasyonu & Passthrough (`scripts/setup_nuvio_presets.sh`)**: Bit-perfect dijital ses passthrough (`sound_digital_output=5`), Dolby Atmos ve HDR10+ dinamik ton eşleme.

---

### 9. 🌤️ Canlı Hava Durumu & Konum Farkındalığı
- **Sıfır Gecikmeli Entegrasyon**: Keyless `wttr.in` servisi üzerinden anlık sıcaklık, hava durumu emojisi ve şehir bilgisi (örneğin `Ankara • +7°C ✨` veya `Istanbul • +14°C 🌤️`) üst bar üzerinde gösterilir.
- Arka planda 30 dakikada bir sıfır bellek tüketimiyle kendini yeniler.

---

### 10. 🔒 AdGuard Private DNS over TLS (DoT)
- Sistem genelinde `dns.adguard-dns.com` şifreli DNS profilini etkinleştirir.
- Reklamları, telemetriyi ve izleyicileri ağ düzeyinde engellerken, ISS kaynaklı DNS engellerini aşar.

---

### 11. 📲 Yerel AirPlay & Kablosuz Yansıtma
- iOS, iPadOS ve macOS aygıtlarından doğrudan AirPlay ses ve video yansıtma desteği.
- Arka plan güç tasarrufu muafiyetiyle 7/24 kesintisiz keşfedilebilirlik.

---

### 12. 🚀 Akıllı Bellek Yönetimi (RAM & zRAM Swap)
- **Geri Dönüşte Otomatik RAM Temizliği (`cleanupBackgroundAppsMemory`)**: Nuvio, YouTube veya ağır bir video/oyun uygulamasından çıkılıp ana ekrana dönüldüğünde (`onResume`), sistem arka planda gereksiz RAM tüketen tüm 3. parti süreçleri sonlandırır ve belleği anında serbest bırakır. Arayüz her zaman ilk açılmış gibi 60 FPS akıcılığını korur.
- **1024 MB LZ4 zRAM Swap**: `/dev/block/zram0` üzerinde sıkıştırılmış bellek diski oluşturur.
- **Swappiness & Cache Tuning**: `vm.swappiness=80` ve `vfs_cache_pressure=100` değerleriyle UI iş parçacıklarını anında yanıt verir tutarken, inaktif servisleri zRAM'e aktarır.
- **RAM Tüketimi**: Başlatıcı bellek kullanımı **35 MB**'ın altındadır.
- **Depolamadan RAM Genişletme**: Android 11+ sanal bellek genişletme (`ram_expand_size_list: 2048`).

---

### 13. 🛡️ Evrensel Debloater & TV-Core Koruma Kalkanı
Fabrika menülerini, telemetriyi ve gereksiz mağaza bloatware'lerini HDMI girişlerini, DVB-S2 uydu alıcısını veya görüntü ayarlarını bozmadan güvenle kaldırır:
- **Marka Profilleri**: Arçelik/Beko, TCL, Sony Bravia, Philips, Xiaomi, Hisense ve Chromecast with Google TV.
- **TV-Core Koruması (`scripts/lib/tv_safety.sh`)**: Donanım çekirdeği paketlerinin (`com.mediatek.wwtv.tvcenter`, `com.tcl.tvmanager`, vb.) silinmesini engeller.
- **Kendi Kendini Onarma (`scripts/restore_tv_core.sh`)**: Yanlışlıkla devre dışı bırakılmış TV çekirdek paketlerini tek komutla kurtarır.

---

### 13b. 🔬 Ekran & HDR Doğrulama Testi (`scripts/verify_display.sh`)
```bash
./scripts/verify_display.sh                  # Hızlı sağlık raporu
./scripts/verify_display.sh --playback-test  # Gerçek 4K HEVC HDR10 klibi oynatarak donanım katmanını ölçer
```
> **"Televizyonum Nuvio'da veya Geliştirici Seçeneklerinde 1080p60 gösteriyor, 4K bozuk mu?"**  
> **Hayır**. MediaTek çipli televizyonlarda (Arçelik, Beko, Grundig, Vestel vb.) Android, arayüzü (UI) 1920×1080@60 olarak işler. Video akışı başladığında ayrı bir donanım video katmanı (hardware overlay plane) devreye girerek paneli yerel 3840×2160 (4K) çözünürlüğe ve `BT2020_ITU_PQ` (HDR10) renk uzayına geçirir. `--playback-test` komutu bunu donanımdan bizzat doğrular.

---

## 🚀 Tek Komutla Evrensel Kurulum Sihirbazı (Setup Wizard)

TV Master Launcher v1.0.0; donanımınızı tanıyan, tercihlerinizi soran ve tüm optimizasyonu tek adımda uygulayan interaktif bir sihirbaz içerir:

```bash
git clone https://github.com/AlptugDag/tv-master-launcher.git
cd tv-master-launcher
./install.sh
```

### Sihirbaz Adımları:
1. **Dil ve Bölge**: Türkiye, ABD, Almanya, İngiltere, Fransa vb. vitrin ve canlı yayın seçimi.
2. **Canlı Hava Durumu**: Şehir belirleme (örneğin Ankara, Istanbul, Berlin) veya IP üzerinden otomatik algılama.
3. **Dublaj & Altyazı Dilleri**: Tercih ettiğiniz diller (`turkish,english` vb.) ile seeder öncelikli Torrentio motoru.
4. **Yapay Zekâ Sinema Küratörü (Google Gemini & Anthropic Claude)**:
   - Dilediğiniz zaman hava durumu, saat ve ruh halinize göre otomatik yaratıcı vitrin seçkileri.
   - API anahtarınızı güvenle TV'nin şifreli deposuna kaydeder (repoda asla tutulmaz).
5. **The Movie Database (TMDB) API**:
   - Ekran koruyucuda ve vitrinde dinamik 4K sinematik sahne arka planları.
6. **Tema Seçimi**: Saf OLED Siyahı, Otomatik Gece/Gündüz veya Aydınlık Buzlu Cam.
7. **Debloat & Güvenli Temizlik**: Üretici bloatware'lerini donanıma zarar vermeden temizleme.
8. **DNS & Reklam Engelleme**: AdGuard DoT veya Cloudflare DoT.
9. **Donanım Kumandası**: Tuş atamaları ve erişilebilirlik servisinin aktifleştirilmesi.

---

## 🤖 Yapay Zekâ ile Otomatik Kurulum (AI Prompt)

Televizyonunuzu ChatGPT, Claude, Gemini veya DeepSeek aracılığıyla da kurabilirsiniz:

1. Televizyonunuza Wi-Fi ADB ile bağlanın:
   ```bash
   adb connect <TV_IP_ADRESINIZ>:5555
   ```
2. [`AI_AUTO_INSTALL_PROMPT.md`](AI_AUTO_INSTALL_PROMPT.md) dosyasını açın.
3. İçeriği kopyalayıp yapay zekâ asistanınıza yapıştırın.
4. Yapay zekâ donanımınızı analiz edecek, en doğru debloat profilini seçecek, RAM'i artıracak ve sinema üssünüzü kuracaktır.

---

## 📂 Depo Yapısı (Repository Structure)

```
tv-master-launcher/
├── AI_AUTO_INSTALL_PROMPT.md        # AI modelleri için ana kurulum promptu
├── LICENSE                          # Apache 2.0 Lisansı
├── README.md                        # Dokümantasyon & görsel vitrin
├── install.sh                       # Tek komutla çalışan interaktif kurulum sihirbazı
├── release/
│   └── tv-master-launcher-v1.0.0.apk # Derlenmiş, imzalanmış üretime hazır APK (v1.0.0)
├── docs/
│   └── screenshots/                 # 1080p canlı video, animasyonlu gif ve ekran görüntüleri
│       ├── cinetv_showcase.mp4      # 1080p canlı kullanım & akıcı vitrin ekran kaydı
│       ├── liquid_glass_glow.gif    # Akıcı neon bloom ve coverflow geçiş animasyonu
│       ├── hero_coverflow_3d.png    # 3D Parallax vitrin (TMDB & Gemini filmografi seçkisi)
│       ├── cinema_hud_overlay.png   # Inline Gemini arama & Floating AI HUD kartı
│       ├── dock_nuvio_showcase.png  # Tam ekran Liquid Glass Tüm Uygulamalar çekmecesi
│       ├── vitrin_rest_overview.png # 4 seçenekli visionOS sistem ayarlar modalı
│       ├── modern_screensaver.png   # TMDB dinamik sahne arka planlı & replikli screensaver
│       ├── focal_neon_amber.png     # Amber neon bloom renk çıkarımı
│       ├── focal_neon_crimson.png   # Crimson neon bloom renk çıkarımı
│       └── focal_neon_cyan.png      # Cyan neon bloom renk çıkarımı
├── launcher/                        # Android Studio kaynak kodları
│   ├── AndroidManifest.xml          # v1.0.0 (versionCode 100)
│   ├── build.sh                     # Hızlı APK derleyici
│   ├── src/                         # Java kaynak kodları (CoverFlow, KeyInterceptor, WebRemote, TMDB)
│   ├── res/                         # Vektörel çizimler, layout'lar, temalar
│   └── assets/                      # Yüksek çözünürlüklü afişler ve yedek veritabanı
└── scripts/                         # Otomasyon betikleri
    ├── install.sh                   # Master interaktif kurulum sihirbazı
    ├── debloat_universal.sh         # Marka algılayıcı evrensel debloater
    ├── debloat_arcelik_beko.sh      # Arçelik / Beko özel debloat betiği
    ├── restore_tv_core.sh           # TV çekirdek paketlerini kurtarma (self-heal)
    ├── verify_display.sh            # Ekran / HDR sağlık raporu ve 4K playback testi
    ├── lib/tv_safety.sh             # Korunan sistem paketleri güvenlik muhafızı
    ├── optimize_ram.sh              # zRAM, swappiness, LMK & bellek ayarları
    ├── setup_dns_and_streaming.sh   # AdGuard DoT & Nuvio 4K HDR10 kurulumu
    ├── setup_nuvio_presets.sh       # Nuvio eklenti ön ayarları & AFR kalibrasyonu
    ├── setup_nova_player.sh         # Nova Video Player harici oynatıcı entegrasyonu
    ├── setup_youtube.sh             # Reklamsız YouTube entegrasyonu
    └── setup_airplay.sh             # AirPlay alıcı yapılandırması
```

---

## 📺 Desteklenen Cihazlar & Markalar

| Üretici / Marka | Test Edilen Modeller | Optimizasyon Profili | Durum |
| :--- | :--- | :--- | :--- |
| **Arçelik / Beko / Grundig** | 4K UHD Smart TVs (MediaTek SoC) | `debloat_arcelik_beko.sh` | Doğrulandı 60 FPS |
| **TCL / Thomson / FFALCON** | C-Serisi, Q-Serisi, P-Serisi | `debloat_universal.sh (Profil B)` | Tam Uyumlu |
| **Sony Bravia** | XR, X80, X90 Serisi | `debloat_universal.sh (Profil C)` | Tam Uyumlu |
| **Philips Android TV** | Ambilight / OLED Serisi | `debloat_universal.sh (Profil D)` | Tam Uyumlu |
| **Xiaomi** | Mi TV 4A/4S/5, TV Box S, TV Stick | `debloat_universal.sh (Profil E)` | Tam Uyumlu |
| **Hisense / Toshiba** | A6/U6/U7 Android Sürümleri | `debloat_universal.sh (Profil F)` | Tam Uyumlu |
| **Google** | Chromecast with Google TV (HD/4K) | `debloat_universal.sh (Profil G)` | Tam Uyumlu |
| **Akıllı Projeksiyonlar** | Anker Nebula, XGIMI, Dangbei, Epson, Formovie, Wanbo (Android TV / AOSP) | `debloat_universal.sh` | Sinema Kalibrasyonu (Dev Ekran) |

---

## 🔒 Güvenlik & Gizlilik Garantisi (Security & Privacy)
- **Sıfır Güvenlik Açığı**: Tüm betikler katı değişken tırnaklama, güvenli IP sanitizasyonu ve güvenli geçici dosya yönetimi içerir.
- **Kişisel Veri İçermez**: Depoda **hiçbir kişisel API anahtarı (Gemini, Claude, TMDB vb.), hesap bilgisi, şifre veya özel imza/filigran yer almaz**. Kullanıcı kendi anahtarlarını kurulum sırasında güvenle girer ve bu anahtarlar yalnızca TV'nin yerel donanım deposunda saklanır.
- Tüm bileşenler standart açık Android SDK ve Leanback API'leri üzerinden çalışır.

---

## 📜 Lisans & Teşekkür
- Bu proje **Apache 2.0 Lisansı** ile lisanslanmıştır. Detaylar için [LICENSE](LICENSE) dosyasına bakabilirsiniz.
- Temel mimari için [**CleanYourTV**](https://github.com/Gathin23/cleanyourtv) (Gathin23) projesine teşekkür ederiz.
