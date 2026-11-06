# Sürüm notları · Release notes

## 1.0.0.53

- **DVD Kutusu Fragman Başlığı Yüksekliği Düzeltmesi:**
  - Açık DVD kutusu fragman modunda (landscape) "Fragman" yazısı ve altındaki el yazısı keçe kalem çizgi efekti, 16:9 chamfered video çerçevesinin (`outerFrame.top`) oldukça yukarısına taşındı (`labelY = sc.top - h * 0.068f`). Böylece başlığın video oynatma yuvası veya çerçeve pahıyla çakışması kesin olarak engellendi.
- **Sıfır Kırpma ile Sinematik Fragman ve YouTube Arayüzünün Tamamen Gizlenmesi:**
  - Fragman videosu kenarlardan asla kırpılmayacak şekilde %100 tam ekran uncropped 16:9 oranına çekildi (`top: 0`, `left: 0`, `width: 100%`, `height: 100%`, `object-fit: contain`).
  - Video oynatılırken YouTube'un varsayılan başlığı, kanal logosu, paylaş butonu ve denetim arayüzü; video yüklenirken devreye giren opak sinematik perde (`tray-trailer-curtain` / `#stage`), oynatma başladığında yumuşak çözünme geçişi, `pointer-events: none` koruması ve WebView içi periyodik stil arındırma (`purgeChrome`) ile video karesini tek bir piksel dahi kesmeden tamamen görünmez kılındı.
- **Temaya Duyarlı Dinamik DVD Yüzey Işık Yansıması ve Parlama Fiziği:**
  - DVD kutularının yüzeyindeki optik ışık yansıması, anlık temaya göre dinamik ve önceden tahsis edilmiş gölgelendiricilerle (zero-allocation theme shaders) yeniden yapılandırıldı:
    - **Likit Cam (Liquid Glass):** 0.038x genişliğinde keskin beyaz kristal ışık çizgisi (`coreShader`), elmas taşlama safir/camgöbeği prizmatik ışık kırılım halesi (`haloShader`), odaklanmış radyal cam parıltısı ve artırılmış jelatin parlaklığı (`1.35x`).
    - **Film Dolabı (Film Closet):** Odaya ve mobilyaya yayılan çok daha yumuşak, sıcak fildişi/krem difüzyon ışığı (`0.16x` genişliğinde yumuşak çekirdek), genişletilmiş kadifemsi ambiyans yansıması ve göz yormayan mat yüzey hissi (`0.68x`).
    - **Sinema Salonu (Cinema Hall):** Sıcak tungsten amber/altın sahne projektör yansıması, kadife kırmızı çevreye uyumlu altın varak halesi.
    - **Metalik (Metallic):** Anizotropik fırçalanmış titanyum/çelik soğuk platin yansıma çizgisi ve lazer pah parlaklığı.
    - **Neon Gece (Neon Night):** Elektrik camgöbeği ve neon fuşya çift tonlu tüp ışık yansıması, yüksek kontrastlı sibernetik parıltı.
    - **CineTV / OLED:** Dengeli stüdyo referans yansıması.
- **Hiper Gerçekçi DVD Kutusu Dış Jelatin (Shrink-Wrap) Efektleri:**
  - Kapalı DVD kutularının dış yüzeyine fabrika çıkışı şeffaf jelatin ambalaj katmanı işlendi.
  - Üst ve alt kenarlarda ısıl yapıştırma dikiş izleri (heat weld seam beads & crimps), sol kenarda kırmızı polipropilen açma şeridi (red tear-strip & pull tab), köşelerde gerçekçi üçgen zarf katlama izleri (envelope corner folds) ve gerilim mikro-kırışıklıkları (tension micro-wrinkles) ile prizmatik ışık parlaması sıfır GC tahsisiyle (pre-allocated static paints) GPU üzerinde çizildi.
- **Temaya Özel DVD İç Metinleri, Künye ve Sırt Tipografisi Senkronizasyonu:**
  - Her temaya özgü tipografi motoru (`FontManager`) genişletildi; DVD sırtlarındaki film başlıkları, kutu içi film özeti, künye detayları ve puan rozetleri temanın görsel kimliğiyle birebir uyumlu yazı tiplerine bağlandı:
    - **Film Dolabı (Film Closet):** Zarif edebi serif (`Playfair Display` / Georgia Serif) başlık ve künye, genişletilmiş harf aralıklı klasik sırt tipografisi.
    - **Sinema Salonu (Cinema Hall):** Teatral ve epik altın varaklı serif (`Cinzel`), klasik gala afişi künye stili.
    - **Likit Cam (Liquid Glass):** Ultra temiz, modern geometrik grotesk (`Syne`), fütüristik kristal şeffaflığıyla uyumlu kompakt sırt yazısı.
    - **Metalik (Metallic):** Endüstriyel monospaced ve teknik mühendislik yazı tipi (`JetBrains Mono`), teknik künye kılavuz çizgileri.
    - **Neon Gece (Neon Night):** Sibernetik yüksek kontrastlı geometrik sans (`Orbitron`), neon ışık haleli başlık ve künye metinleri.
    - **OLED Siyahı (OLED):** Saf İsviçre neo-grotesk (Roboto / Helvetica), mutlak kontrastlı saç teli kalınlığında künye.
- **Film Dolabı Temasında Parlak Gri İç Materyal Yenilemesi:**
  - Film Dolabı (Closet) temasında DVD kutusunun içindeki eski kahverengi ahşap dokusu tamamen kaldırıldı.
  - Yerine fırçalanmış lüks parlak gri lake kabuk (`#353942`), koyu antrasit tepsi (`#585E6B` - `#24272E`), saten platin refleksiyon çizgileri ve metalik disk kilit göbeği uygulandı.
- **Temaya Özel Değişen DVD Kutu İçi Kağıt Tipleri:**
  - **Artvellum (Film Dolabı):** İnce dokulu, sıcak fildişi presli el yapımı kuşe kağıt ve kör gofre çerçeve baskısı.
  - **Programme (Sinema Salonu):** 24 ayar çift altın yaldızlı festival gala kitapçık kağıdı.
  - **Acrylic (Likit Cam):** Buzlu yarı saydam optik aydınger (frosted vellum) sayfa dokusu.
  - **Aluminium (Metalik):** Lazer çizgili, mikro-ölçekli teknik mühendislik paftası.
  - **Neon (Neon Gece):** UV laklı derin obsidyen mat karton dokusu.
  - **OLED (OLED Siyahı):** Sıfır ışık sızdırmalı ultra mat minimalist grafit kart stoğu.
- **Sürüm ve Paket İlerlemesi:** Android TV başlatıcı derleme sürümü (`launcher/build.gradle`), Windows PC arayüzü (`windows/index.html`) ve servis işçileri (`sw.js`) 1.0.0.53 seviyesine yükseltildi.

## 1.0.0.52

- **Hiper Gerçekçi ve Temaya Göre Özelleştirilmiş 3B Fiziksel Raf Mimarisi:**
  - **Sinematik 3D Derinlik ve Perspektif İllüzyonu:** Raf derinliği (`shelfDeckDepth`) genişletilerek 3B açılı DVD kutularının gerçekten derin bir mobilya tablası üzerinde durduğu fiziksel etki güçlendirildi. Arka duvara yaslanma gölgesi (`shelf-wall-shadow`), derin perspektifli üst tabla (`shelf-deck` / `rotateX`), kutu tabanlarının cilalı yüzeydeki yansıması (`shelf-deck-reflection`), odak spot ışığı (`shelf-deck-spotlight`), kutu temas gölgesi (`shelf-contact-shadow`), dikey alınlık (`shelf-fascia`), tepe ışık pahı (`shelf-apex-bead`), dekoratif oyuk çıta (`shelf-molding-lip`), alt gölge pahı (`shelf-undercut-groove`) ve odaya süzülen ayak ışığı yıkayıcı (`shelf-underglow`) ayrık 3B katmanlar olarak yapılandırıldı.
  - **Sinema Salonu (Cinema Hall):** 80px derinliğinde 56 derece açılı zengin maun ağaç tablası, masif cilalı pirinç kavisli ön pervaz (`#EEBE3E`), merkezde 14px ışıma yapan tepe altın ışık çizgisi (`#FFF8D6`), antika pirinç gölge oyuğu çıtası ve kadife kırmızı arka plana sıcak tungsten amber sahne ayak ışığı yayılımı.
  - **Film Dolabı (Film Closet):** 72px derinliğinde İskandinav açık dişbudak/meşe doğrama ahşap dokusu, saten fırçalanmış nikel pervaz (`#D8DBE2`), 5000K galeri spot aydınlatması, hassas gölge oyuk çizgisi ve yumuşak ortam temas gölgesi.
  - **Likit Cam (Liquid Glass):** 76px derinliğinde 22 mm optik kristal cam tabla, iç toplam yansıma kırılma hattı (`refractionPath`), elmas taşlama safir-camgöbeği pah parıltısı (`#38BDF8`), cam altı prizmatik safir ışıma havuzu ve yarı saydam derinlik.
  - **Kırmızı Halı (Red Carpet):** Cilalı koyu kızıl pelesenk/gül ağacı tablası, 24 ayar altın varaklı kavisli ön bordür, altın kakma çıta ve festival galası sıcak zemin ışıltısı.
  - **Metalik (Metallic):** CNC işleme havacılık sınıfı anodize titanyum kütük, yüzeyde mikro-çizgili fırçalanmış doku (hairline grain shader), 45 derece lazer kesim keskin titanyum pahı, koyu füme ön alınlık ve hassas işleme kılavuz oluğu.
  - **Neon Gece (Neon Night):** Ultra parlak siyah obsidyen ayna tabla, ön alınlığın hemen altına entegre edilen kesintisiz neon ışık tüpü ve alt zemine radyan elektrik camgöbeği ile neon fuşya ışıma yayan ambiyans yıkayıcı.
  - **OLED Siyahı (OLED):** Mutlak kontrast için sıfır ışık sızdırmalı mat karbon kompozit raf, minimalist grafit saç teli pahı ve derin siyah zemin gradyanı.
  - **CineTV İmzası (Varsayılan):** Piyano cilalı kompozit yüzey, masif platin pah hattı ve çift tonlu camgöbeği-çivit mavisi atmosferik ışık yansıması.
- **TV Başlatma Sırasında TTS Karşılama Senkronizasyonu Düzeltmesi:**
  - Önceki oturumun önbellekteki eski vitrini ekrandayken Gamze TTS motorunun "Vitrini hazırladım" / "Bu akşam için vitrini hazırladım" anonsunu yapmasına yol açan başlatma yarış durumu (race condition) giderildi.
  - Başlatma karşılama sayacı, taze vitrin hazırlanırken (`needFresh`, `freshPending`, `isCurating` veya bayat vitrin durumu) yeni küratörlük sonucu gelene kadar arka planda zarifçe bekletilir; yeni vitrin film kapaklarıyla ekrana indiği anda (`setShelf`) anons senkronize olarak tetiklenerek kullanıcının gerçekten yeni hazırlanmış vitrini gördüğü anda karşılanması garanti altına alındı.
- **Sürüm ve Paket İlerlemesi:** Android TV başlatıcı derleme sürümü (`launcher/build.gradle`), Windows PC arayüzü (`windows/index.html`) ve servis işçileri (`sw.js`) 1.0.0.52 seviyesine yükseltildi.

## 1.0.0.51

- **Sürekli, Yavaşlatılmış 3 Boyutlu (Yaw, Pitch, Roll + Derinlik) Odak DVD Salınımı ve Senkronize Işık Fiziği:** Odaktaki DVD kutusunun 3B jiroskopik salınımı 13.5s–21.0s periyotlu harmonik dalgalarla yavaşlatıldı ve odakta kaldığı sürece kesintisiz (`INFINITE`) devam edecek şekilde güncellendi. Kutu yüzeyindeki optik parlama (surface flare), açılı pencere yansıması (window reflection streak), sırt menteşe pahı (spine bevel catchlight) ve arka plan ambiyans halesi 3B normal açılarıyla birebir senkronize hareket eder.
- **Hiper Gerçekçi 3B Açık DVD Kutu İçi Tasarımı (Disk ve Fragman Modları):**
  - **Merkezi Polipropilen Menteşe Oluk Yapısı (Living-Hinge Spine Gutter) ve Dış Kabuk:** Sol kapak ile sağ tepsi arasına çift katlama kanallı, derin kitap-katı ambiyans gölgeli (ambient occlusion) 3B merkezi sırt oluğu, üst/alt kilit dudakları, dış kenar başparmak açma girintileri ve çift çıtçıtlı kilit tırnakları eklendi.
  - **12 Yapraklı Split-Collet "PUSH" Göbek ve Derin Parmak Kuyuları:** Sağ tepside 12 radyal esneme yarıklı ve tutucu dişli rozet göbek, içbükey kabartmalı "PUSH" butonu, disk dışına taşan 3B eğimli ergonomik parmak çıkarma kuyuları, köşe enjeksiyon kalıp cepleri ve yarı saydam polikarbonat disk göbek halkası pre-baked olarak işlendi.
  - **3B Konsol Kitapçık Klipsleri ve Silindirik Sayfa Gölgelendirmesi:** Sol kapakta kalıp boşluk pencereli 3B polipropilen tutucu klipsler, sırt kıvrımına doğru silindirik kağıt hacim gölgesi ve çok katmanlı kuşe sayfa kenarları oluşturuldu.
- **Basılı Tutarak Kaydırmada Kesintisiz Momentum ve Domino Dalgası:** Vitrinde yön tuşlarına basılı tutulduğunda her adımda animasyonun sıfırlanmasından kaynaklanan takılma kaldırıldı; hız koruyucu sürekli fizik döngüsü (continuous velocity-preserving spring tracker) ve C2 Hermite domino dalgası ile kutular su gibi akıcı biçimde taranır, tuş bırakıldığında seçilen kutu sönümlü yay fiziğiyle odak konumuna yerleşir.
- **Varsayılan Kalıcı DVD Kutusu Görünümü:** 3B fiziksel DVD kutusu görünümü tüm cihazlarda kalıcı varsayılan olarak sabitlendi ve Ayarlar menüsündeki açma/kapama anahtarı kaldırıldı.
- **Kusursuz "CineTV'yi Yok Et" (Destroy CineTV) Deneyimi:** Kaldırma sürecinde kurulum öncesi sistem ayarları, bildirim dinleyici izinleri, Nuvio koleksiyon dosyaları ve önbellek profilleri eksiksiz temizlenir; fabrika başlatıcısının (stock launcher) ayağa kalktığı doğrulanmadan işlem tamamlanmayarak cihazın boş ekranda kalması kesin olarak engellenir.
- **4 Katmanlı Shazam Müzik Tanıma & Mavi Tuş Düzeltmesi:**
  - Kumandadaki Mavi tuşun (`KEYCODE_PROG_BLUE` / scanCode `186`, `401`) ana ekranda TV+ başlatmaya çalışması engellenerek doğrudan Shazam müzik tanıma tetikleyicisine bağlandı.
  - Mikrofonu olmayan veya sessiz PCM akışı döndüren TV'lerde sahte sessiz kaynağın seçilmesi engellendi; aktif medya oturumu (`MediaSession`), akustik `0xCAFE2580` parmak izi + Apple iTunes yüksek çözünürlüklü kapak zenginleştirmesi ve sahne/film müziği analiziyle her koşulda kesintisiz müzik tespiti sağlandı.
- **Kumanda Görünümünde İnce Ayarlı Dokunsal Titreşim (Nuanced Haptic Feedback) Motoru:** Mobil kumanda görünümünde etkileşim türüne göre özelleştirilmiş titreşim profilleri (`CineTvHaptics`) eklendi; OK ve tıklama dokunuşlarında keskin darbe (`[22, 18, 10]`), uzun OK basışında çift darbeli onay (`[28, 30, 42]`), Air Mouse imleç hareketinde ve kaydırma (scroll) etkileşimlerinde hıza duyarlı kesintisiz yumuşak uğultu (`[4, 14, 4]` / `[5, 16, 6]`), yön adımları, ses/kanal mandalları, medya tuşları ve güç onayında ayrıştırılmış dokunsal geri bildirim sağlandı.
- **Kararsız İnternet Bağlantıları İçin Service Worker Tabanlı Afiş ve Meta Veri Önbellekleme Mimarisi:** Film afişleri (`cinetv-posters-v1.0.0.51`, Cache-First + arka plan Stale-While-Revalidate + TMDB yansı yedeklemesi + çevrimdışı SVG yer tutucu), film/dizi katalog meta verileri (`cinetv-metadata-v1.0.0.51`, hızlı zaman aşımına sahip Network-First + Stale-While-Revalidate + katalog anlık görüntüsü sentezi) ve uygulama kabuğu (`cinetv-shell-v1.0.0.51`) için çok katmanlı Service Worker önbellekleme stratejisi devreye alındı; bağlantı koptuğunda veya dalgalandığında vitrin, arama ve film detayları kesintisiz ve tepkisel çalışmaya devam eder.
- **Sürüm ve Paket İlerlemesi:** Build, release, APK ve Windows web platform sürüm numaraları 1.0.0.51 olarak güncellendi.

## 1.0.0.50

- **Vitrin DVD Rafı Lazy Loading & Açılış Gecikme Optimizasyonu:** Vitrindeki DVD kutu kapakları `IntersectionObserver` mimarisiyle ekrana yaklaşırken dinamik ve gecikmesiz olarak yüklenir. Raf açılışındaki ilk yükleme blokajı tamamen ortadan kaldırıldı; iskelet yer tutucular (skeleton) ve pürüzsüz opaklık geçişleriyle akıcı bir görsel başlangıç sağlandı.
- **Donanım Hızlandırma ve 3D Transform Optimizasyonu:** Gereksiz 3D dönüştürmeleri ve GPU bellek aşımını önlemek amacıyla `will-change: transform` özelliği yalnızca odak ve aktif komşu kutularla sınırlandırıldı; ekran dışındaki kutular görünümden kaldırılarak (`display: none`) matris hesaplama yükü sıfırlandı. Tüm dönüşümler `translate3d` ve `scale3d` ile doğrudan GPU kompozisyonuna alındı (`contain: layout`, `backface-visibility: hidden`).
- **Hiper Gerçekçi DVD Kutu İçi ve Fragman Deneyimi:**
  - **Fiziksel Fragman Tepsisi:** Fragman modunda (`landscape`) video ekranını çevreleyen açılı (chamfered) 16:9 mat bezel çerçevesi, köşe alyen perçinler, alt kenar teknik kuşağı ("16:9 ANAMORPHIC WIDESCREEN · HIGH DEFINITION MASTER · 24 FPS PREVIEW"), optik yeşil/mavi LED göstergesi, sertleştirici kaburgalar ve polikarbonat ışık yansıması tek seferde pre-baked matris olarak çizildi.
  - **Orijinal Amaray Kitapçık ve Tırnak Detayları:** Sol kapakta çok sayfalı kuşe kağıt katman kalınlığı, sırt katlama çizgisinde metalik zımba telleri, döküm gölgeli ve tırnak kilitli polipropilen tutucu klipsler eklendi.
  - **Optik Disk Yatağı ve Mekanik Göbek:** Sağ tepside çift parmak çıkarma girintisi, sertleştirici petek kaburgalar ve 8 tırnaklı yay mekanizmalı "PUSH" çıkarma butonu pre-baked olarak uygulandı; açılış ve oynatma ses efektleri (`SystemAudio.Sfx.POP`) senkronize edildi.
- **Sürüm ve Paket İlerlemesi:** Build, release ve Windows web platform sürüm numaraları 1.0.0.50 olarak güncellendi.


- **ADB Kapalıyken Tam Bağımsız Çalışma ve Otomatik Arka Plan Güncellemesi:** Ağ Hata Ayıklama (ADB) tamamen kapatılsa dahi telefon kumandası, yön tuşları, klavye, ekran görüntüsü, CineSUB, APK kurulumu ve CineTV kendi kendine arka plan güncellemeleri (`UPDATE_PACKAGES_WITHOUT_USER_ACTION` + `TvKeyInterceptorService` erişilebilirlik köprüsü) kesintisiz çalışır.
- **Mobil Kumanda Medya Sekmesi ve Sanatçı Filmografisi:** Mobil arayüzdeki Shazam sekmesi **Medya** olarak yenilendi; film kartlarında yönetmen ve oyuncu kadrosu fotoğraflarıyla listelenir, kişiye dokunulduğunda tüm filmografisi açılır.
- **Akıllı Bağlamsal Vitrin ve Kusursuz Arama:** Vitrin başlıkları şehir, hava durumu, mevsim ve günün saatine göre özgün sinema kuşakları üretir; birleşik kavram aramaları ve benzer film önerileri daraltılmış yüksek isabetle çalışır.
- **Sürüm ve Paket İlerlemesi:** Build, release ve APK sürüm numaraları 1.0.0.49 olarak güncellendi.

## 1.0.0.48

- **Spatial HUD Standartlaştırıldı:** HUD açıp kapama ayarı ayarlardan kaldırıldı ve bütün cihazlarda standart olarak varsayılan ve açık hale getirildi.
- **Akıcı ve Dinamik Ana Sayfa Animasyonları (Ease In-Out):** Ana sayfadaki tüm animasyonlar aniden başlayıp bitmek yerine sinematik visionOS cubic ease-in-out (`UiKit.EASE_IN_OUT`) eğrisiyle güncellendi.
- **Yeni DVD Kutuları Smooth Geliş Deneyimi:** Ana ekrana yeni gelen DVD kutuları düz soluk geçiş yerine derinlikten yükselen, yumuşak ölçeklenen (0.92x -> 1.0x) ve hafif ötelenen pürüzsüz ease-in-out geçişiyle rafa oturuyor.
- **Canlı Gamze Hologram Avatarı (Mobil Arayüz):** Mobil web kumandadaki Gamze bölümünden emojiler kaldırıldı; yerine gerçek zamanlı mimik yapan, konuşma esnasında visem/fonem uyumlu dudak oynatan ve göz kırpan fütüristik VisionOS kadın yüzü hologramı eklendi.
- **Kullanıcı Arayüzü Marka Temizliği:** Gamze sohbet modallarından ve kullanıcı ekranlarından gereksiz "Google" ifadeleri kaldırılarak temiz ve doğrudan asistan kimliği sağlandı.
- **Sürüm ve Paket İlerlemesi:** Build, release ve APK sürüm numaraları 1.0.0.48 olarak güncellendi.

## 1.0.0.47

- **Sessiz ve Kesintisiz Arka Plan Güncellemeleri:** Kullanıcı TV izlerken veya film esnasında ekranı bölmeden güncellemelerin arka planda indirilip uygulanması sağlandı.
- **Türkçe TTS Sayı ve Yıl Telaffuz İyileştirmeleri:** 1930 gibi tarih ve yılların rakam rakam okunması engellenerek doğal Türkçe ("bin dokuz yüz otuz") okuması sağlandı.
- **Akıllı TV Performans Modları:** Donanıma göre dinamik ölçeklenen otomatik TV optimizasyonu ve VisionOS Liquid Glass mobil kumanda revizyonu tamamlandı.

## 1.0.0.46

- **Nuvio'da Sesli CineSUB Deneyimi:** Kumandasında mikrofon tuşu olan TV'lerde Nuvio'da film/dizi izlerken kumandadaki mikrofon tuşuna basıldığında ekranda hiçbir kutucuk ya da kart çıkmadan video karesi anında dondurulur, ekranın kenarlarından büyüleyici golden glow konuşma animasyonları yayılır. Kullanıcının sorduğu soruya ("Bu oyuncunun adı ne?", "Bu sahnede gözüken kırmızı şey ne?", "Şu arabanın modeli ne?") Gemini Vision ile doğrudan sahneden analiz edilerek sesli yanıt (TTS) verilir ve yanıt bitince film otomatik olarak kaldığı yerden oynamaya devam eder.
- **Mavi Liquid Glass Shazam Müzik Tanıma:** Shazam ile müzik aramada TV ekranının dört kenarında elektrik mavisi / camgöbeği akustik dalga animasyonu (`ShazamOverlay`) ve ekranın alt kısmında 18 saniye boyunca kalan, albüm kapaklı **Liquid Glass** şarkı bilgi kapsülü eklendi.
- **Mobil Arayüzde Shazam Taranan Şarkılar & Modern Tasarım:** Mobil arayüzdeki dosya yöneticisi kaldırılarak yerine Shazam'da taranan şarkıların geçmişi eklendi; bulunan şarkılara dokunulduğunda doğrudan **YouTube Music** ve **Spotify** üzerinde açılması sağlandı. Mobil arayüz ferahlatılarak yüzen Liquid Glass alt bar ve modern kart yerleşimiyle yenilendi.
- **Sürüm ve Paket İlerlemesi:** Build, release ve APK sürüm numaraları 1.0.0.46 olarak güncellendi.

## 1.0.0.45

- **Kumanda ses ve kanal tuşlarında sürekli değişim:** TV kumandasında ses artırma/azaltma ve kanal tuşlarına basılı tutulduğunda tek birim takılması giderildi; sürekli akıcı değişim (repeat rate) sağlandı.
- **Otomatik CineSUB geliştirmeleri:** Ekrandaki içeriğin yanlış analiz edilmesi önlendi, dizi analizi (sezon/bölüm) ve YouTube/video etiketleri temizlenerek doğru içerik tespiti sağlandı; bilgi kartlarının üst üste binmesi önlendi.
- **Nuvio açıkken mobil arayüz bağlantısı:** Nuvio oynatımı esnasında arka planda web remote ve key bridge bağlantısının kopması engellendi.
- **Gelişmiş Türkçe TTS (EmaLightning):** Yabancı yönetmen, aktör ve film adları, DVD gibi kısaltmalar, kesme işaretli ekler ve parantez içi ifadeler için doğal ve akıcı Türkçe telaffuz kuralları getirildi.
- **Erişilebilirlik ve ekran oranları:** Sistem erişilebilirlik ayarlarına doğrudan yönlendirme eklendi; ekran oranı ve yazı tipi için "çok küçük" ve "en küçük" seçenekleri eklendi.
- **Arçelik TV bellek koruması:** Düşük bellekli Android TV'lerde Nuvio oynatımı esnasında arka plan yükü ve çökme/yeniden başlama engellendi.
- **CineTV'yi Yok Et geliştirmesi:** CineTV kaldırılırken kurulum öncesi orijinal TV ayarlarına (varsayılan launcher, font ölçeği, animasyonlar) tam geri dönüş sağlandı.
- **DVD kutusu sırt yazıları:** Sırt yazılarının geç yüklenmesini önlemek için anında oluşturulan tek tip net black font standardı uygulandı.
- **Mobil dosya ve ekran görüntüsü yöneticisi:** Web remote üzerinden TV dosya sistemi (/sdcard) ve çekilen ekran görüntüleri doğrudan yönetilebilir hale getirildi; dikey mobil ekranlarda Cineparty genişlik uyumu sağlandı.

## 1.0.0.44

- **"Oynatma Kullanılamıyor" düzeltmesi bu sürümde de var:** Nuvio'da yayın eklentisi yoksa CineTV onu bir kez kurmayı teklif ediyor (1.0.0.43'teki düzeltme).
- **TV'nin kendi launcher'ı her açılışta denetleniyor:** Kapatılmışsa ya da silinmişse yeniden açılıyor, böylece CineTV kaldırıldığında TV pembe ekrana düşmüyor.
- **GitHub yayını:** Yayınlanacak yeni sürüm olmadığında yayın adımı artık hata vermiyor, atlanıyor. Sürüm yine CineTV'nin her zamanki anahtarıyla imzalanıyor, bu yüzden kurulu CineTV'ler güncelleme alabiliyor.

## 1.0.0.43

- **CineTV kaldırılınca pembe ekran yok:** CineTV, ana ekran olmak için TV'nin kendi başlatıcısını kapatıyordu. CineTV TV'nin Ayarlar'ından kaldırılınca o başlatıcı kapalı kalıyor ve TV ana ekransız, pembe ekrana düşüyordu. Artık TV'nin başlatıcısı hiç kapatılmıyor; CineTV ana ekran tuşunu sistemin kendi "varsayılan ana ekran" rolüyle alıyor. Daha önceki sürümlerin kapattığı başlatıcılar bu güncellemeyle yeniden açılıyor, ana ekran yine CineTV kalıyor.
- **Nuvio'da "Oynatma Kullanılamıyor":** Nuvio "Oynat" düğmesini ancak film bağlantısı (stream) veren bir eklenti kuruluysa açıyor; katalog, bilgi ve altyazı eklentileri bu işi yapmıyor. Nuvio'su önceden kurulu olan TV'lere ve CineTV'nin resmi Nuvio'ya geçirdiği TV'lere bu eklenti hiç kurulmuyordu. CineTV artık bunu bir kez Nuvio'ya ekliyor (Nuvio onay isteyebilir).
- **Oyuncu resimleri:** AIOMetadata ayar dosyası daha önce indirildiyse artık sadece boş API anahtarları dolduruluyor, kullanıcının ayarları korunuyor. TVDB anahtarı yoksa diziler için TMDB kullanılıyor; anahtarsız TVDB dizi sayfalarını oyuncusuz ve IMDb numarasız bırakıyordu.
- **Yayın:** GitHub'daki 1.0.0.42 derlemesi, silinen hata ayıklama anahtarı (debug.keystore) yüzünden APK kontrolünde duruyordu; kontrol artık o dosya olmadan da çalışıyor.

## 1.0.0.42

- **Nuvio Kaynak Seçiminde Çökme & Yeniden Başlama Giderildi:** Nuvio akış ve kaynak ararken bellek kullanımının zirveye ulaştığı anlar tespit edildi; kaynak seçim pencerelerinde arka plan ekran yakalama ve CineSUB işlemleri tamamen durdurularak Low Memory Killer (OOM) kaynaklı launcher çökmeleri önlendi.
- **Aynı Türkçe İsimli Filmlerde Doğru Kaynak Eşleşmesi:** Türkçe isimleri birebir aynı olan filmlerde (örneğin *Equilibrium* ve *Mutiny* her ikisi de "İsyan") orijinal İngilizce isim ve yapım yılı (`originalTitle` ve `year`) korunarak Nuvio'ya iletiliyor; kaynak seçim ekranında doğru filmin gelmesi garanti altına alındı.
- **"Kaldığın Yerden Devam Et" İyileştirmeleri:** 
  - Nuvio'daki süre içeren devam butonları ("Devam Et (42 dk)", "Devam Et (35:10)") tam olarak tanınıyor; asenkron yüklemeler tamamlanmadan aceleyle "Oynat"a basılarak filmin en baştan başlaması engellendi.
  - Aynı isimdeki farklı bir filmin açılması `originalTitle + year` ve IMDb/TMDB kimlikleri ile engellendi.
- **Ses Tuşuna Basılı Tutma ve Ses Seviyesi Ayarlayıcı:** Kumandadaki ses açma/kısma tuşlarına basılı tutulduğunda sesin kesintisiz akıcı şekilde artıp azalması sağlandı; TV'nin dahili "Ses Seviyesi Ayarlayıcı" / AVL fonksiyonlarının engellenmesi önlendi.
- **Anında Açılan 3D DVD Vitrini (0ms Başlangıç):** TV yeniden başlatıldığında rafın uzun süre boş kalıp sonradan belirmesi sorunu giderildi; önbellekteki DVD kutuları açılışta sıfır gecikmeyle (0 ms) hemen ekrana basılıyor.
- **Açılışta Gamze Sesli Selamlama Düzeltildi:** Soğuk açılışta Gamze'nin konuşma balonu görünmesine rağmen sesin çıkmaması sorunu, TTS motorunun bağlanma sürecinde sesli bildirimlerin kuyruğa alınıp bağlanma tamamlandığında seslendirilmesiyle giderildi.
- **Sessiz Saatler Özelliği Kaldırıldı:** Kullanıcı isteği doğrultusunda "Sessiz Saatler" ses kısıtlaması ve ayarları tamamen sistemden kaldırıldı.
- **CineSUB Manuel Mod Tarama Hızlandırması:** Sarı tuş ile yapılan manuel sahne analizinde ekran yakalama boyutu 640px JPEG formatına optimize edildi; `gemini-2.5-flash` ve `gemini-2.5-flash-lite` modellerine doğrudan erişilerek tarama süresi saniyeler seviyesine indirildi.
- **Ekran Görüntülerinin Telefona Otomatik Kaydedilmesi:** Mavi tuşa basılı tutarak alınan ekran görüntüleri, telefon kumandası açıkken doğrudan telefona otomatik olarak indirilir ve galeriye kaydedilir.
- **CineQuiz Anında Soru Yükleme:** CineQuiz sorularının bekleterek yüklenmesi sorunu giderildi; sorular yerel kütüphane üstverileriyle anında ekrana gelir, yapay zekâ zenginleştirmeleri arka planda asenkron işlenir.
- **SnakePhile Oyunu Geliştirmeleri:** Film havuzu 32 klasik ve yerli başyapıt filme genişletildi; filmler yutulduğunda Gamze sesli tepkileri gecikmesiz anında gelir ve telefon kumandasındaki yön kontrol tuşları büyütüldü.
- **CineTV Turu Başlatma & Yenilik Rehberi:** Ayarlar menüsündeki CineTV Turu'na basıldığında 16 adımlı sesli tur anında başlatılıyor; gereksiz "Yenilik Rehberi" katmanı kaldırıldı.
- **Kumandadaki SARI Tuş ile Benzer Filmler:** Ana sayfada iken kumandanın SARI tuşuna basıldığında, vitrindeki odakta bulunan filmin benzerlerinin vitrinde listelenmesi özelliği CineTV Turu'na ve belgelere eklendi.
- **Mavi Tuş Shazam Entegrasyonu:** Kumandanın MAVİ tuşuna basıp çekildiğinde ortamda çalan müzik mikrofonla dinlenip Gemini ses analizi ve iTunes üzerinden şarkı/sanatçı ve albüm kapağıyla tanınır, Gamze seslendirir ve telefon kumandasına bildirim düşer (basılı tutma ekran görüntüsü almaya devam eder).
- **Otomatik CineSUB Nuvio'da Tam Uyumlu:** Nuvio içinde video oynatılırken `am.isMusicActive()` ve stream başlıklarındaki "1080p", "bluray" gibi etiketler yüzünden taramanın engellenmesi giderildi. Nuvio'da film izlenirken (`idle > 12s`) otomatik CineSUB sahneleri ve altyazı göndermelerini YouTube gibi sorunsuz ve akıcı şekilde algılar.
- **Bluetooth Kulaklık Kopmaları Engellendi:** Bluetooth kulaklık bağlıyken arka planda yapılan periyodik A2DP bağlantı denemeleri ve sesli komut başlatılırken A2DP stereo bağlantısını koparan SCO profil geçişi engellendi; Bluetooth kulaklıklarda yaşanan ani ses kesintileri ve kopmalar tamamen çözüldü.
- **Kurulumda API Anahtarları İsteğe Bağlı Hale Getirildi:** İlk kurulum sihirbazında ve güncelleme sonrasında API anahtarlarının girilmesi zorunlu olmaktan çıkarıldı; anahtar girilmezse bazı özelliklerin (afiş, fragman, AI vitrin, CineSUB) kısıtlı çalışabileceğini belirten bilgilendirici uyarı eklendi ve anahtarsız devam etmeye izin verildi.
- **Nuvio Ayarlarının ve Profillerinin Korunması:** Güncellemelerde kullanıcının mevcut Nuvio ayarları, eklentileri, şablonları ve profilleri kontrol edilerek üzerine yazılması kesin olarak engellendi.

## 1.0.0.41

- **CineSUB'nin kendiliğinden notları akıllı ve yeniden açık:** TV, ekrana 15 saniyede bir (düşük bellekli TV'de 30 saniyede bir) kendisi bakıyor; bu bakış yapay zekâya hiç sorgu göndermiyor. Yapay zekâya sadece sahne değiştiğinde ya da yeni bir altyazı satırı çıktığında soruluyor. Böylece önemli anlarda notlar daha sık geliyor, durgun anlarda hiç sorgu harcanmıyor.
- **Kota korunuyor:** İki sorgu arasında en az 40 saniye (düşük bellekli TV'de 90 saniye) var; üst üste boş çıkan bakışlarda bu süre 6 dakikaya kadar uzuyor, ilginç bir şey bulununca kısalıyor. Kendiliğinden notlar sadece Flash-Lite'ı kullanıyor (günlük ücretsiz hakkı geniş) ve günde en fazla 300 sorgu yapıyor; Flash'ın küçük günlük hakkı tamamen sarı tuşa kalıyor. Anahtarın ücretli ya da ücretsiz olmasına bakılmıyor, herkes için aynı.

## 1.0.0.40

- **Kumandadan ekran görüntüsü:** Mavi tuşu bir saniye basılı tutunca TV'nin o anki ekranı alınır ve mobil arayüzü açık olan telefonda hemen belirir; oradan kaydedilebilir ya da paylaşılabilir. Görüntü sadece TV'nin belleğinde durur, diske yazılmaz. Mavi tuşa kısa basış eskisi gibi çalışır, sarı tuş CineSUB olarak kalır.

## 1.0.0.39

- **CineSUB yeniden çalışıyor:** Sarı tuşla açınca "Yapay zekâ servisi HTTP 400 döndürdü" yazıyordu. 1.0.0.35'te çalışan model adları geri geldi. Bir model ayarı reddederse (HTTP 400) CineSUB artık durmuyor; aynı isteği sade haliyle tekrar deniyor, olmazsa sıradaki modele geçiyor. 2.5 Flash-Lite'a reddettiği düşünme bütçesi artık gönderilmiyor.
- **Arçelik'te Nuvio içinde launcher'ın yeniden başlaması:** 1.0.0.38 kendiliğinden gelen CineSUB notlarını herkese varsayılan olarak açmış ve her 15 saniyede bir ekran görüntüsü alıyordu. Bu 1,3 GB'lık TV'nin belleğini dolduruyordu. Bu notlar artık yine isteğe bağlı (CineSUB ayarlarından açılır), kapalıyken film oynarken hiçbir şey yapılmıyor; açıkken de daha seyrek bakılıyor.
- **Telefondan yapıştırma TV'ye gidiyor:** Mobil arayüzün klavye alanına yapıştırılan metin TV'deki seçili alana tek seferde yazılıyor. Daha önce harf harf gönderiliyor, zaman aşımına düşüyor ve TV klavyesinin yazamadığı karakterler kayboluyordu. Hızlı yazılan harfler de artık kaybolmuyor.
- **Nuvio depoları:** Nuvio'nun kendi içinde zaten bulunan Cinemeta, CineTV'nin kurduğu eklentilere ikinci kez eklenmiyor. Her güncellemede Şifreli DNS (DNS over TLS) kendiliğinden açılmıyor; sadece kullanıcı seçtiyse uygulanıyor.

## 1.0.0.38

- **Zorunlu Kimlik ve API Anahtarı Kurulumu (Kurulumda ve Güncelleme Sonrasında):**
  - Kurulum esnasında ve her uygulama güncellemesi sonrasında cihazda **Kullanıcı Adı** (Gamze sesli asistanın TTS ile size hitap edebilmesi için), **TMDB API Anahtarı** (yerelleştirilmiş Türkçe film özetleri, posterler, oyuncu kadrosu ve fragmanlar için) ve **Google Gemini API Anahtarı** (Gamze yapay zekâ asistanı ve CineSUB sahne analizi için) kontrol edilir.
  - Bu üç bilgiden herhangi biri eksikse, sistemin bunlar olmadan düzgün çalışmayacağını açıkça belirten ve tamamlanmadan kapanmayan zorunlu kurulum ekranı otomatik olarak açılır.
  - Kullanıcıların anahtarları saniyeler içinde alabilmesi ve telefondan tek dokunuşla yapıştırabilmesi için ekrana **3 adet QR kod** (`themoviedb.org/settings/api`, `aistudio.google.com/apikey` ve Telefon Kumandası bağlantı QR kodu) eklendi; ayrıca telefon kumandasına `/api/keys` ve `/api/setup` uç noktaları ile hem TMDB v3 hem v4 (`eyJ...`) anahtarlarını otomatik algılama desteği getirildi.
- **Kapsamlı ve Etkileşimli CineTV Turu:**
  - Kurulumda ve Ayarlar'da yer alan CineTV Turu; 8 ana bölüm rozeti, segmentli görsel ilerleme çubuğu, her adıma özel kumanda kısayol/ipucu kutuları, ilgili adımlarda doğrudan taranabilir QR kodlar, `▲`/`▼` ile bölüm atlama ve Gamze'nin kullanıcıya adıyla hitap eden sesli rehber anlatımıyla (`Sarı Tuş` ile açılıp kapatılabilir) baştan sona yenilendi.
- **Tetiklenmeden / Otomatik Çalışan CineSUB Eşlik Modu Güçlendirildi:**
  - Otomatik CineSUB (`CineSUB Eşlik Modu`) tüm kurulumlarda ve güncellemelerde **varsayılan olarak açık** hâle getirildi.
  - Hem standart ses hem de Dolby/DTS passthrough oynatmalarda medya oynatımı algılanarak kontrol sıklığı **15 saniyelik** döngülere, sahne tarama aralığı ise uyarlanabilir **20–40 saniyeye** indirildi.
  - Otomatik analiz artık tek seferde **1 ila 2 zengin detayı** (tanınan filmlerde oyuncu/karakter ve kamera arkası bilgisi, çekim mekânları ve mimari yapılar, tam araba/saat/kostüm/obje modelleri, tablolar/sanat eserleri, sahne müziği/besteci, yönetmen motifleri ve altyazıda geçen deyim, argo, tarihsel olay veya film/edebiyat göndermeleri) aynı anda algılıyor; tekrarları önlemek için son gösterilen başlıkları hafızada tutuyor ve ekrandaki nesneleri zarif hedef halkalarıyla işaretliyor.

## 1.0.0.37

- **Nuvio'da "Oynatma Kullanılamıyor" ve eksik oyuncu fotoğrafları giderildi:**
  - Nuvio'ya temel IMDb katalog/üstveri sağlayıcısı (**Cinemeta**) eklendi ve önbellekteki film kartlarının Nuvio'ya gönderilmeden önce `tt...` IMDb kimliklerinin tamamlanması sağlandı.
  - Xperience ve AIOMetadata yapılandırmalarındaki oyuncu sınırı `cast_limit: 15` olarak açıldı; TV'deki mevcut TMDB ve MDBList anahtarları şablonlara otomatik işleniyor.
  - TV'de önceden kurulu resmi Nuvio varsa eklenti ve hesap verileri artık sıfırlanmıyor; yalnızca eksik eklentiler tamamlanıyor.
- **Kumanda Geri (Back) tuşu ve Ana Ekran davranışı düzeltildi:**
  - Geri tuşu artık TizenTube (YouTube) veya Nuvio içindeki ayar/altyazı/kaynak menülerinden çıkarken sizi Ana Ekran'a atmıyor; yalnızca CineTV'den doğrudan açılmış bir film kartının en üst sayfasından çıkarken Ana Ekran'a döndürüyor.
  - Ana Ekran'da Geri tuşuna basıldığında odağın üst satırlara (kategori/üst çubuk) sıçraması engellendi.
- **"Kaldığın yerden" rafı Nuvio ile tam senkronize:**
  - Nuvio'nun kendi izleme listesinden kaldırılan içerikler tespit edildiğinde veya kart üzerindeki Sil tuşuna basıldığında içerik CineTV'nin "Kaldığın yerden" rafından da anında kaldırılıyor.
- **CineSUB (Sarı tuş) hızlandırıldı:**
  - Yavaş `screencap` komutu yerine Android 11+ `AccessibilityService.takeScreenshot()` donanım arabelleği (~100 ms) ve `thinkingBudget: 0` yapılandırmalı `gemini-2.5-flash` / `gemini-2.5-flash-lite` kullanılıyor; sahne analizi tutarlılığı korunarak saniyeler içinde tamamlanıyor.
- **DVD kutusu fragman sesi geri geldi:**
  - DVD kutusu açıldığında sesli fragman akışı (muxed stream + yumuşak ses yükselişi) yeniden aktif.
- **Android 14 erişilebilirlik ayarları:**
  - Standart erişilebilirlik ekranını gizleyen Android 14 TV'ler için alternatif sistem bileşenleri ve `LocalAdb` üzerinden doğrudan servis etkinleştirme yedeği eklendi.
- **Bölgesel film kategorileri ve vitrin tazeleme:**
  - Türk, Kore, Japon, Hint ve Avrupa sineması kategorileri TMDB `with_original_language` filtresiyle ve çevrimdışı kürasyon yedekleriyle birbirinden tamamen ayrıldı; kategori seçiliyken "Vitrini Tazele" ilgili kategorinin sonraki sayfalarını getiriyor.
- **Donanım ve yazılıma özgü otomatik görüntü, ses, ağ ve performans optimizasyonu (her güncellemede arka planda):**
  - MediaTek (`MtkTvConfig`), Amlogic (`hdr_policy`), Realtek ve standart Android TV motorları tespit edilerek keskinlik, hareket yumuşatma (MEMC), gürültü azaltma ve renk sıcaklığı otomatik kalibre ediliyor; yalnız gerçek donanım çözücüler (`AvOptimizer.isHardwareDecoder`) 4K/HEVC/AV1/VP9 için doğrulanıyor.
  - HDMI eARC/ARC, Optik, USB DAC ve Bluetooth LE Audio ses zinciri tespit edilerek bit-perfect passthrough / PCM ve örneklem hızına duyarlı (`sampleRate`) AirPods uzamsal ses filtresi uygulanıyor.
  - Wi-Fi tarama daraltması, güç tasarrufu kaynaklı paket gecikmesi (jitter) önleme ve hızlı DoT (Cloudflare/AdGuard) doğrulaması eklendi; yayın profili yüksek jitter ve düşük RAM durumuna göre kendiliğinden ölçekleniyor.
  - Her uygulama veya sistem güncellemesinde (`MY_PACKAGE_REPLACED` ve `BOOT_COMPLETED`) tüm donanım/yazılım optimizasyonları ve ART `speed-profile` derlemesi arka planda otomatik çalışıyor; R8 tam mod ve paketleme temizliğiyle APK boyutu ve çalışma hızı ileri seviyeye taşındı.

## 1.0.0.36

- **DVD kutusundan Nuvio oynatma davranışı geri geldi:** OK, normal filmde önce Nuvio'nun "Oynat" düğmesine basar; oynatma mı kaynak seçimi mi olacağını Nuvio ayarı belirler. "Kaldığın yerden" kartı ise 1.0.0.35'teki gibi devam düğmesini önceliklendirir.
- **Ayarlar menüsü:** En üstteki tıklanabilir kategori kısayolları kaldırıldı.

## 1.0.0.35

- **Kaldığın yerden doğrudan devam ediyor:** Karta basınca DVD kutusu açılmıyor, film doğrudan açılıyor. Nuvio'da CineTV "Devam Et" düğmesine (dizide sıradaki bölümün düğmesine) kendisi basıyor ve Nuvio kendi kaydettiği dakikadan devam ediyor; AlptugPlayer CineTV'nin kaydettiği dakikadan devam ediyor.
- **Nuvio'nun kendi oynatıcısı da varsayılan olarak kaydediliyor:** İzleme konumunu okumak için gereken Bildirim Erişimi artık TV'nin adb'si ile CineTV tarafından veriliyor; Ayarlar'da aramak gerekmiyor. "CineTV'yi yok et" bu izni geri alıyor.
- **Önce "Devam Et":** Kaydedilmiş dakikası olan bir filmde Nuvio "Oynat" ile birlikte "Devam Et" gösterdiğinde artık baştan başlatan "Oynat" değil, "Devam Et" seçiliyor.

## 1.0.0.34

- **Root gerektirmeyen sıkıştırılmış bellek:**
  - Android'in uygulama sıkıştırması açılıyor: arka planda bekleyen uygulamaların belleği TV'nin kendi sıkıştırılmış takas alanına (zRAM) taşınıyor, öndeki uygulamaya (ör. Nuvio) daha çok RAM kalıyor. Bazı TV'lerde kapalıydı (Arçelik'te kapalı, Grundig'de açıktı); CineTV her açılıştan sonra yeniden açıyor.
  - CineTV kendi görsellerini bellekte sıkıştırılmış tutuyor: bellek darlaşınca çözülmüş resimler bırakılıyor, sıkıştırılmış kopyalar (onda bir boyutta) kalıyor; resim yavaş depolamadan değil bellekten yeniden çözülüyor.
  - Bellek ekranı TV'nin takas alanını, kullanımını, uygulama sıkıştırmasının durumunu ve CineTV'nin sıkıştırılmış görsellerini gösteriyor.
  - Önceki "2 GB sanal RAM / zRAM büyütme" komutları kaldırıldı: root gerektirdikleri için hiçbir TV'de etkili olmuyorlardı. Açıklamalar buna göre düzeltildi.
  - "CineTV'yi yok et" uygulama sıkıştırmasını TV'nin eski hâline döndürüyor (yalnızca CineTV açtıysa).
- **GitHub sürümleri artık kendi imzalanıyor:** İmza anahtarı depo gizlilerine eklendi; bir sürüm etiketi gönderildiğinde GitHub aynı anahtarla imzalayıp yayınlıyor.

## 1.0.0.33

- **Tam kontrol yeniden varsayılan:** CineTV, TV'nin Ana Ekran rolünü alıyor ve diğer başlatıcıları bir kez kapatıyor; "Hangisini seçmek istersiniz?" sorusu kendiliğinden çıkmıyor (1.0.0.32'de bu yalnızca Ayarlar'dan seçilebiliyordu).
- **"CineTV'yi yok et" her şeyi geri alıyor:** Kapatılan başlatıcılar geri açılıyor, CineTV Ana Ekran rolünü bırakıyor ve TV'nin kendi başlatıcısı yeniden varsayılan Ana Ekran oluyor. Google Asistan ve asistan rolü de CineTV'den önceki hâline dönüyor. Bunlar geri gelmezse kaldırma işlemi durup bildiriyor.

## 1.0.0.32

- **Nuvio'dayken CineTV kendini baştan başlatmıyor:** 1,3 GB'lık TV'de Nuvio izlerken bellek bitince Android sırayla Nuvio'yu ve CineTV'yi kapatıyordu, Ana Sayfa da kendiliğinden geri geliyordu. Bellek darlaştığında ve CineTV ekranda değilken telefon kumandasının tarayıcı motoru (~100 MB) ile raf görselleri bırakılıyor; Ana Sayfa'ya dönünce geri geliyor.
- **Açılışta düz logo yok:** Açılış penceresindeki logo saydam arka planlı; kırmızı ışığın üstünde siyah bir kutu olarak görünmüyor.
- **Gamze isim uydurmuyor:** AI ve sesli yanıtlar artık herkese "Deniz" diye hitap etmiyor. İsterseniz Gamze ayarlarından bir hitap adı seçin; boş bırakırsanız isim kullanmaz.
- **Vitrin arama rafı sabit:** Yeni sonuçlar yüklenirken hazır DVD kutuları yerinde kalıyor; sonraki sayfalar rafın sonuna ekleniyor.
- **Güncelleme kontrolü ve bildirimi:** Otomatik kontrol, arka plan denemeleri ve kaçırılmış güncelleme bildirimi daha güvenilir.
- **TV ayarlarına tek tuş:** Ayarlar içindeki ayrı TV menüleri kaldırıldı; tek "TV Ayarları" tuşu televizyonun kendi ayarlarını açıyor.
- **Ayar penceresi kenarları:** Odak ve tıklama animasyonundaki öğeler artık pencere sınırında kırpılmıyor.
- **Nuvio'da Kaldığın yerden:** Özellik varsayılan olarak açık. Nuvio'nun kendi oynatıcısındaki yeni izlemeleri yerelde kaydetmek için Ayarlar'dan Bildirim Erişimi'ni ayrıca vermeniz gerekir; geçmiş izlemeler içe aktarılmaz ve bildirim içeriği okunup saklanmaz. Kart Nuvio'da başlığı açar; devam konumunu Nuvio kendi geçmişinden kullanır.
- **Gamze'nin dinamik yerel hafızası:** Açıkça paylaşılan tür, sevilen/sevilmeyen yapımlar, içerik sınırları, birlikte izleme, dil/ülke, oyuncu/yönetmen ve süre tercihleri sonraki arama, ek sonuç, günlük seçki ve yanıtlara yalnızca hafif öneri sinyali ekler; hiçbir tercihi zorunlu filtreye dönüştürmez. İzlenen film tek başına beğeni sayılmaz; hafıza Ayarlar'dan görüntülenip temizlenebilir.
- **Başlatıcı ve kaldırma güvenliği:** CineTV artık diğer TV başlatıcılarını kendiliğinden kapatmıyor; bu yalnızca Ayarlar'dan açıkça seçilebilir. "CineTV'yi yok et", kaydedilmiş paket/başlatıcı geri yüklemesi başarısızsa veya başka kullanılabilir Ana ekran yoksa durur; kaldırmayı sessiz adb komutu yerine Android'in kendi ekranına bırakır.

## 1.0.0.31

- **Bir basış, bir adım (Arçelik, Beko, Grundig):** MStar kızılötesi alıcısı kumandanın tekrar sinyalini (~108 ms sonra) yeni bir basış olarak bildiriyordu; kısa bir basış odağı vitrin, simgeler ve ayar panelleri dahil her yerde iki kez ilerletiyordu. Aynı ok tuşu 170 ms içinde gelirse aynı basışın yankısı sayılıyor. Hızlı art arda basmak ve basılı tutmak çalışmaya devam ediyor.
- **Vitrin hep Gamze'nin Seçimi:** Açılışta, bir saatlik aradan sonra, tazelemede ve yapay zekâ yanıt vermediğinde vitrin artık başka bir kategoriye geçmiyor; hep Gamze'nin Seçimi olarak geliyor ve sekme işareti de Gamze'de kalıyor. Yapay zekâ yoksa kategoriyi Gamze seçiyor, başlık yine onun. Başka bir raf yalnızca siz bir sekme seçince ya da arama yapınca geliyor.
- **Kategori sekmeleri seçilince yükleniyor:** Bütün kategorilerin arka planda önceden yüklenmesi kaldırıldı; bir sekmenin filmleri ona tıklayınca geliyor. Açılışla hiçbir şey yarışmıyor.
- **Nuvio artık kendi güncellemelerini alabiliyor:** Erken bir kurulumun CineTV anahtarıyla imzaladığı Nuvio, Nuvio'nun güncellemelerini hiçbir zaman kuramıyordu ("Uygulama yüklenmedi"). CineTV böyle bir kopyayı bir kez Nuvio'nun resmi en yeni sürümüyle (betalar dahil) değiştiriyor ve eklentilerini ile ayar dosyalarını geri kuruyor. Resmi ya da başka birinin Nuvio'suna hiç dokunulmuyor; Nuvio açıkken bir şey yapılmıyor; indirme yarım kalırsa eski kopya duruyor. Nuvio'nun kendi kitaplığı ve ayarları bu tek seferde sıfırlanıyor.
- **Otomatik testler:** Vitrin başlığının gerçek anla uyumu (ay, şehir, günün saati), saat temizliği, sesli istekte alternatif duyuşlar ve güncelleme sürüm karşılaştırması için yeni testler eklendi; her GitHub gönderiminde çalışıyor (toplam 24 test).

## 1.0.0.30

- **Google Asistan tamamen devre dışı, mikrofon Gamze'de:**
  - CineTV, TV'nin asistan rolünü alıyor; Google TV'nin asistan uygulaması (katniss) yalnızca konuşma tanıma başka bir yerden (Google TTS) geliyorsa kapatılıyor.
  - Kumandanın ses tuşu artık doğrudan Gamze'yi açıyor; Google'ın asistanı hiç devreye girmiyor.
  - Konuşma anlaşılmıyordu: 1.0.0.27'deki "asistanı durdur" adımı, Google TV'de konuşma tanımayı da yürüten uygulamayı durdurup mikrofonu sağır bırakıyordu. Artık hiçbir şey durdurulmuyor; tanıma Türkçe çevrimiçi ve çevrimdışı motorla başlıyor.
  - "CineTV'yi yok et" her şeyi eski haline getiriyor.
- **Açılış hızı:**
  - Her açılışta bütün uygulamaların önbelleği (CineTV'nin afişleri dahil) ve sistemin dosya önbelleği siliniyordu; açılıştan sonra her şey yavaş depolamadan yeniden yükleniyordu. Bu kaldırıldı.
  - Kurulum adımları (adb izinleri, Nuvio kontrolü, bellek ayarı) artık her açılışta değil, sürüm başına bir kez ve TV açıldıktan 90 saniye sonra yapılıyor.
  - TV'nin kendi tuner uygulaması açılışta ve her Ana Sayfa dönüşünde kapatılıyor, TV onu her seferinde baştan başlatıyordu. Artık yalnızca gerçekten ekranı aldığında kapatılıyor.
  - Açılışta CineTV dört kez başlatılıyordu; artık bir kez ve bir yedek.
  - Telefon kumandası sunucusu, tuş köprüsü ve sesler açılıştan sonraya, telefonun ağ dışı bağlantısı ise açılıştan en az 3 dakika sonraya bırakılıyor.
  - Uygulama kutucuklarının görselleri ve odanın arka planı artık ana iş parçacığında değil, arka planda hazırlanıyor.
  - Açılış animasyonu oynarken arkasındaki Ana Sayfa çizilmiyor; her açılış karesi artık yalnızca animasyonun kendisi.
  - AirPlay alıcısını açık tutan adım her Ana Sayfa dönüşünde ve bir zamanlayıcıyla yaklaşık 30 saniyede bir, her seferinde sekiz kadar Android komut işlemi başlatıyordu (açılış sırasında da). Artık açılıştan iki dakika sonra, en fazla yarım saatte bir ve yalnızca alıcı çalışmıyorsa yapılıyor. Boşta TV'de artık hiç komut işlemi başlamıyor.

## 1.0.0.29

- **Açılış (ek düzeltmeler):**
  - TV açıldığında ağ henüz hazır değilse vitrin yenileme "yapıldı" sayılıp önceki gecenin rafı saatlerce kalıyordu. Artık yalnızca yeni bir raf gerçekten geldiğinde sayılıyor; gelene kadar 15 saniyede bir ve ağ bağlanır bağlanmaz yeniden deneniyor.
  - Önceki rafa hiç dönülmüyor: zamanında gelmezse ekranda "Yeni vitrin hazırlanıyor…" yazıyor.
  - Boş kutu kabukları kaldırıldı. Kapak hazır oldukça her kutu, odaktan dışarı doğru kısa bir yerleşme animasyonuyla rafa konuyor; zayıf TV'lerde daha sakin, güçlülerde daha hızlı.
  - Açılış penceresi animasyonun ilk karesiyle aynı (kırmızı ışık dahil); animasyon kesintisiz devralıyor. Açılış daha kısa: yapay zekâ için 4 saniye, en fazla 8 saniye.
  - Gamze'nin karşılaması artık açılış animasyonu bitip Ana Sayfa göründükten sonra geliyor.
- **Tek basış, tek adım:** TV meşgulken kısa bir basışın tekrarları basılı tutma sanılıyor, odak birden çok kutu ilerliyordu. Artık kumandanın kendi zaman bilgisi kullanılıyor; ilk 400 ms içindeki tekrarlar her yerde (raf, simgeler, uygulamalar) yok sayılıyor.
- **Ekran koruyucu:** Açılıştan sonraki ilk ekran koruyucuda film görüntüsü yoktu. Görüntüsü gelmeyen kare artık gösterilmiyor, birkaç saniye sonra başka bir film deneniyor.
- **Açılışta eski vitrin artık yok:**
  - TV açıldığında, bekleme modundan çıktığında ve Ana Sayfa'ya bir saatten uzun aradan sonra dönüldüğünde önceki vitrin hiç gösterilmiyor.
  - Açılış animasyonu yeni vitrin hazır olana kadar ekranda kalıyor.
  - Yapay zekâ 6 saniyede yanıt vermezse yine yepyeni bir kategori vitrini geliyor; geç gelen yanıt sonradan vitrini değiştirmiyor.
- **Açılış ekranı:**
  - Logo artık kesik bir kart içinde değil, siyah üzerinde.
  - Eski sürümlerdeki kırmızı, kalp atışı gibi atan ışık geri geldi.
  - Normal açılış daha kısa sürüyor.
- **Ana ekranı tamamen devralma:** CineTV, TV'nin kendi adb'si ile Ana Ekran rolünü sessizce alıyor ve diğer başlatıcıları bir kez kapatıyor. "Hangisini seçmek istersiniz?" penceresi artık kendiliğinden hiç açılmıyor. Ayarlar'dan ve "CineTV'yi yok et" ile her şey eski haline dönüyor.
- **Tek Ana Ekran:** Home tuşu ile CineTV'nin kendi açılışları farklı görevlere düşüp ana ekranın birden fazla kopyasını açabiliyordu; her kopya bütün raf görsellerini bellekte tutuyordu (1,3 GB'lık bir TV'de 13 kopya bulundu). Artık her zaman tek kopya var.
- **İçerik açarken:**
  - Nuvio'nun "Oynat" düğmesi yine kendiliğinden basılıyor, doğrudan kaynak seçimine ya da oynatmaya geçiliyor. Nuvio 1.1'de düğme yazının birkaç kat yukarısında olduğu için bulunamıyordu.
  - Dizilerde hiç izlenmediyse bölüm listesine geçiliyor; izlendiyse kaldığınız bölümden devam ediliyor.
- **Fragmanlı kutularda OK:** Disk animasyonu yok; fragman çerçevesi büyüyüp bütün ekranı kaplıyor, kararıyor ve film açılıyor.
- **Temaya özel kutu içi:** Kâğıt tutucular (pirinç klips, altın varak, krom kelepçe, şeffaf akrilik, neon tüp), kutunun iç kaplaması (keçe, kadife, ceviz, fırçalanmış metal, buzlu akrilik, neon ızgara) ve disk göbeği her temada farklı. Yazılar her temada aynı el yazısıyla, eski boyut ve yerleşimiyle.
- **Kutular daha hızlı doluyor:** Bir raftaki bütün afişler aynı anda paralel indiriliyor; her kapak yalnızca kendi afişini bekliyor. Sırttaki film logoları daha kısa bekliyor.
- **Uzamsal ses (her kulaklık):**
  - Panel artık "Kulaklık & Uzamsal Ses"; Bluetooth, kablolu, USB ve BLE kulaklıklar ile işitme cihazları tanınıyor.
  - 7.1 ve mono filmler de işleniyor (önceden hiç etkilenmiyordu).
  - "Sanal Surround" artık oda yansımalarıyla belirgin bir oda hissi veriyor.
  - Not: Bu işlem AlptugPlayer'da çalışıyor. Nuvio'da etkili olması için Nuvio'nun dış oynatıcısı olarak "CineTV Oynatıcı" seçilmeli; Android başka bir uygulamanın sesini işlemeye izin vermiyor.
- **Ses tanıma:** En güvenilir duyulan cümle seçiliyor, diğer olası duyuşlar da Gamze'ye veriliyor; yanlış duyulan film ya da kişi adı bağlamdan düzeltiliyor.
- **Tema adları** yeniden Türkçe (1.0.0.28'deki derleme araçları güncellemesi bu metinleri pakete almıyordu).

## 1.0.0.28

- **Vitrin başlıkları:**
  - "İstanbul Ocak şafağında…", "İzmir Eylül sabahında…" gibi yanlış başlıklar artık gelmiyor. Tazele tuşu yapay zekâya gerçek anı (saat, gün, ay, hava) göndermiyordu; şimdi her zaman gönderiyor.
  - TV şehrini bilmiyorsa artık "İstanbul" uydurulmuyor.
  - Her başlık kodla denetleniyor: başka bir ay, gün, saat dilimi ya da şehir içerirse yerine temanın kendisi yazılıyor.
  - Filmlerin başlıktaki temaya açıkça uyması isteniyor.
- **Vitrin ne zaman yenilenir:** TV her açıldığında ve Ana Sayfa'ya bir saatten uzun bir aradan sonra her dönüşte vitrin yepyeni önerilerle geliyor. Açılışta yenileme kumandaya dokunulmadığı ya da birkaç saniye dokunulmadığı bir anda yapılıyor, gezinmenin ortasında değil.
- **Açılış:**
  - Siyah ekran yerine anında CineTV logosu görünüyor; AlptugPlayer açılırken de.
  - Telefon kumandasının ağ dışı bağlantısı için çalışan gizli tarayıcı motoru artık açılışta başlamıyor; başlangıçta ana iş parçacığını yaklaşık 3 saniye kilitliyordu ve yaklaşık 55 MB bellek kullanıyordu. QR kodu açıldığında ya da son iki haftada kullanıldıysa 40 saniye sonra başlıyor.
  - Kategori sekmelerinin önyüklemesi açılıştan 45 saniye sonra ve ekran sakinken yapılıyor.
- **Akıcılık:**
  - Kutular arasında gezinirken bir basışta odak birkaç kutu ilerlemiyor: TV meşgulken biriken eski tuş tekrarları atılıyor.
  - Fragman hazırlığı yalnızca bir kutuda durduğunuzda başlıyor ve düşük öncelikte çalışıyor; YouTube çözümleme çizimle yarışmıyor.
  - Kapak hazırlayan iş parçacıkları çizimin altında öncelikte çalışıyor.
- **Tek performans modu (Yüksek), canlı ölçümle:**
  - Düşük/Orta/Yüksek seçimi ve ayarı kaldırıldı; her TV her efektle çalışıyor.
  - CineTV kareleri anlık ölçüyor; ekran yavaşladığında logolar, kategori önyüklemesi ve fragman hazırlığı ekran sakinleşene kadar bekliyor. Hiçbir efekt kapanmıyor.
- **DVD kutuları:**
  - Kutu ve gölgesi artık birlikte geliyor.
  - Bütün raf, ortak boş kutu olarak anında ayağa kalkıyor; her filmin yalnızca kapağı geliyor. Kutunun plastik gövdesi, menteşe, parlama ve kenar ışığı bir kez hazırlanıyor (aynı piksel, çok daha az iş).
  - Fragmanlı kutularda da OK'e basınca fragman yerini filmin diskine bırakıyor: disk dönerek kalkıyor ve öne geliyor.
- **Arama:** Sonuçların ekrandaki kutuları, afişleri paralel indirilip hazır olarak geliyor; tek tek dolmuyor.
- **Kaldığın yerden kartı:** Oynatıcı konumu kaydettiği anda ana sayfadaki kart güncelleniyor; ayarı kapatıp açmak gerekmiyor.
- **Cam paneller:** Ayarlar ve diğer cam paneller Orta/Yüksek'te de yarı saydam; buzlu katman arkayı tamamen kapatmıyor.
- **Daha küçük, daha yeni:**
  - APK 4,22 MB'tan 3,79 MB'a indi. Yalnızca CineTV'nin konuştuğu diller tutuldu; NewPipe'ın yalnızca kullanılan YouTube kısmı kaldı.
  - Media3 1.11.1 (oynatıcı ve fragmanlar), OkHttp 5.5.1, Android Gradle Plugin 9.5.0 ve Gradle 9.8.2.
  - Hâlâ Android 6 ve üzerine kuruluyor; eski paket yükleyicileri için v1 imzası duruyor.
- **Not:** Yapay zekâ yanıt ve önerilerinin kalitesi, kullandığınız yapay zekâ hesabına göre değişebilir. Ücretsiz anahtarlar daha küçük modellere, daha düşük kotaya ve daha yavaş yanıtlara yönlendirilebilir.

## 1.0.0.27

- **Ana sayfa:**
  - Film bilgileri ana sayfada artık gösterilmiyor; eskisi gibi yalnızca kutu açılınca görünüyor. Rafın adı yine kapsülde.
  - Vitrinin altındaki kategoriler artık küçük, yalnızca yazıdan oluşan sekmeler. Uygulama tepsisi aşağı kaydırmadan görünüyor.
  - Kategoriler her 50 dakikada yeni bir sırayla geliyor ve her biri farklı filmler getiriyor.
  - "Şu an sinemalarda" kategorisi kaldırıldı.
- **Vitrin tazeleme:** Yapay zekâ az film önerdiğinde (özellikle ücretsiz anahtarlarda) vitrin artık 4 kutuyla açılmıyor. Raf, ekrana gelmeden önce benzer filmlerle tam boyuta tamamlanıyor; sonradan da hiçbir kutu yer değiştirmiyor.
- **Güncelleme (Grundig ve diğer TV'ler):**
  - İndirilen dosyanın boyutu doğrulanıyor; yarım inen dosya bir kez yeniden indiriliyor.
  - Önceki yarım kalmış kurulum oturumları temizleniyor.
  - "Bilinmeyen uygulamaları yükle" izni eksikse TV'nin izin sayfası açılıyor; CineTV'ye dönünce kurulum kendiliğinden sürüyor.
  - Kurulum onayı CineTV arka plandayken gelirse kaybolmuyor; CineTV'ye dönülünce gösteriliyor.
  - Kurulum başarısız olursa TV'nin kendi yükleyicisi dosyanın üzerinde açılıyor. O da olmazsa gerçek neden Türkçe yazılıyor (yer yok, farklı imza, güvenlik engeli…).
  - Otomatik güncelleme bulununca ekranda bildirim çıkıyor.
- **Mikrofon tuşu (Grundig, Arçelik):** Kumandanın sesi doğrudan Google Asistan'a gönderdiği TV'lerde asistan anında kapanıyor ve Gamze açılıyor. Önceden, Gamze'yle konuştuktan sonraki 20 saniye içinde basılırsa asistan açık kalıyordu.
- **Arama:** "deniz filmleri" gibi aramalara artık adı Deniz olan kişilerin filmleri gelmiyor. Bir kişi ancak sorulan her kelime adında geçiyorsa seçiliyor; tek kelime yalnızca soyadı ya da tam ad olarak sayılıyor. Deniz, orman, çöl, dağ ve uzay konu olarak aranıyor. Ücretsiz yapay zekâ hesaplarında da aynı kural geçerli.
- **Mobil arayüz:**
  - "Uzay Yalnızlığı" kategorisinin adı artık "Yalnız"; tek başına izlemelik filmler öneriyor.
  - "Ailece", "Arkadaşlar", "Sevgiliyle" ve "Yalnız" artık 3-4 sonuçta kalmıyor. Liste kendiliğinden doluyor, kaydırdıkça aynı havada yeni filmler geliyor. Yapay zekâ yoksa benzer filmlerle devam ediyor.

## 1.0.0.25

- AirPods/kulaklık: "Sanal Surround" ve "Geniş Sahne" artık AlptugPlayer'da her TV'de duyuluyor. Android'in yerleşik Virtualizer efektini birçok TV üreticisi hiç uygulamıyor (açık görünüp sese dokunmuyor); ses artık CineTV'nin kendi işlemcisinde işleniyor. Sanal Surround'da her kulak diğer kanalı biraz gecikmeli ve yumuşamış duyuyor, 5.1 arka kanallar arkaya ve yanlara yerleşiyor. Geniş Sahne'de stereo görüntü genişliyor, orta korunuyor. Seçim film oynarken anında değişiyor.

## 1.0.0.24

- **Android 6 ve üzeri:** CineTV artık Android 6.0'dan itibaren kuruluyor (önce 8.0'dı). Fire TV Stick'lerin çoğu (Fire OS 6) ve eski Android 6/7 TV kutuları da kapsanıyor. Android 7 ve sonrasına özgü her özellik sürüm kontrolüyle korunuyor; eski sürümde o özellik sessizce atlanıyor, uygulama çökmüyor.
- **AirPods paneli:**
  - TV kumandasıyla gezilip tıklanabiliyor; tuşlar artık doğrudan panele gidiyor.
  - "Sanal Surround" ve "Geniş Sahne" gerçekten uygulanıyor. Efekt artık her oynatıcının kendi ses oturumuna bağlanıyor: AlptugPlayer'a doğrudan, Nuvio ve diğer oynatıcılara Android'in ses oturumu yayınıyla. Kulaklık bağlıyken AlptugPlayer efekt uygulanabilsin diye tünelsiz oynatıyor.
- **Arama rafı:**
  - Az sonuçla başlayan arama hemen doluyor.
  - Yeni sonuçlar rafın sonuna 14 kutu kala ekleniyor, böylece ekrandaki kutular yer değiştirmiyor.
- **Güvenlik:**
  - Windows kurulum sayfasındaki TMDB anahtarı kaldırıldı.
  - Sırlar artık her CI çalışmasında tüm geçmişte taranıyor.

## 1.0.0.23

1.0.0.22'nin iki betasındaki her şey ve şunlar:

- **Nuvio'da film açılmıyordu:** "Yüklü eklentilerin hiçbiri movie için yayın desteği sunmuyor" hatasının nedeni bulundu. IMDb kimliği olmayan bir film için CineTV, aynı TMDB numarasıyla bir diziye bakıp alakasız bir IMDb kimliği gönderiyordu. Artık yalnızca filmin kendi kimliği gidiyor; IMDb kimliği hiç yoksa TMDB kimliği gidiyor. Film hiçbir durumda Nuvio'nun arama ya da ana sayfasına düşmüyor.
- **Fragman oynaması:** Otomatik "Oynat" artık yalnızca gerçek Oynat düğmesine basıyor; fragman alanındaki "Play" simgelerine ve büyük alanlara basmıyor.
- **AlptugPlayer:**
  - P2P yayınlarda torrent sunucusu parçaları hazırlarken verdiği geçici hatalar sabırla yeniden deneniyor, film aynı saniyeden sürüyor.
  - Web'den gönderilen uzantısız HLS/DASH bağlantıları ("container unsupported") tanınıyor.
- **Ayarlar › Sistem:**
  - "Erişilebilirlik" artık CineTV'nin kendi sayfası: Yüksek kontrast, Hareketi azalt, Arayüz ve yazı boyutu, TV'nin erişilebilirlik ayarları.
  - "Varsayılan Başlatıcı" sayfası: CineTV'yi sistem onayıyla varsayılan yapma, diğer başlatıcıları kapatma (isteğe bağlı) ve TV'nin başlatıcısını geri getirme.
- **Mikrofon:** Google asistanı mikrofonu bırakmadan tanıma başlamıyor. Mikrofon alınamazsa bir kez daha deneniyor, sonra TV'nin kendi sesli araması açılıyor. Bluetooth kulaklık mikrofonu yalnızca gerçekten bağlıysa kullanılıyor.
- **Kaldırılanlar:** Film görselinin tema arka planına yansıması ve Letterboxd (ayarları, soruları, kayıtlı değerleri ve bağlantılarıyla) tamamen kaldırıldı.
- **Nuvio güncellemeleri:** Nuvio'ya kendi güncellemesini kurma izni veriliyor. Ayarlar'a "Nuvio'yu Güncelle" eklendi.
- **Vitrin:** Ana sayfaya her dönüşte değil; yalnızca TV açıldığında, 3 saat sonra ya da yenileme tuşuyla yenileniyor.
- **Telefon Keşfet:** Aşağı kaydırınca yeni filmler ekleniyor. Yenileme her seferinde farklı filmler getiriyor (TMDB anahtarı olmadan da).
- **CineSUB:**
  - Açıkken oynayan film duruyor, kapanınca (yalnızca önceden oynuyorsa) devam ediyor; bitiş, geri, hata ve süre dolması dahil her çıkışta.
  - Gamze kartın başlığını değil yalnızca içeriği okuyor.
- **Ana sayfa:** Film künyesi (logo, yıl, süre, tür, puan, özet) ve AlptugPlayer'da yarım kalan film için "Kaldığın yerden" kartı. Ayarlar'da "Sinematik Sahne" ile kapatılabilir.
- **GitHub iş akışları:** Node.js 24 sürümlerine geçti, Ubuntu sürümü sabitlendi.

## 1.0.0.22b · Beta 2

- CineQuiz: soru TV'de her zaman tam görünüyor. Resim küçüldü, soru sütunu genişledi; uzun sorular kesilmek yerine sığana kadar küçülüyor. Süre halkası resmin altına alındı, cevaplar sorunun hemen altında.
- Oyunlarda Gamze bekletmiyor: yapay zekâ sesi 3,5 saniyede hazır olmazsa TV'nin kendi sesi okuyor. SnakePhile'da film notu bir sonraki cümleyle kesilmiyor.
- Telefonu eşleştirme: TV'deki izin sorusu artık hangi uygulama açık olursa olsun (Nuvio, oynatıcı, oyun) üstte çıkıyor. Telefon yeniden denediğinde (Samsung'da ana ekrana ekleme) aynı soruya bağlanıyor; eskiden yeni bir istek açıyor ve TV'nin cevabı boşa gidiyordu.
- CineQuiz ayarları: telefonda zorluk, kategori ve süre herkes tarafından seçilebiliyor, Başlat herkese açık. Odadan çıkan ya da eski bir kimlikte kalan "ev sahibi" devrediliyor.
- CineSUB otomatik mod:
  - AlptugPlayer'da hiç çalışmıyordu (oynatıcı CineTV'nin içinde olduğu için atlanıyordu); artık filmin gerçekten oynayıp oynamadığına bakıyor.
  - Otomatik mod açıkken oynatıcı tünelsiz açılıyor, böylece ekran görüntüsü siyah kalmıyor.
  - Bakamadığında 3 dakika yerine 1 dakika sonra yeniden deniyor.
  - Gamze konuşurken, ekran kapalıyken veya kota bittiğinde susuyor.
  - Android 11+ TV'lerde adb kapalıysa ekranı erişilebilirlik servisiyle yakalıyor.
- Uyumluluk:
  - Android 14'te uygulama içi güncelleme sessizce başarısız oluyordu; sistem ses alıcısı da işaretsizdi.
  - Türkçe TV'de "I" harfinin "ı"ya dönüşmesi HTTP başlıklarını, paket adlarını ve tema anahtarlarını bozuyordu.
  - Arapça/Farsça dilli TV'lerde eşleşme anahtarı ve renk kodları farklı rakamlarla yazılıyordu.
  - Android 12+ Bluetooth izni olmadan AirPods kontrolü çökebiliyordu.
  - Yalnızca kablolu ağı olan veya mikrofonsuz TV'ler de kurabiliyor; küçük harfli HLS türü de oynatıcıya geliyor.

## 1.0.0.22a · Beta 1

- AlptugPlayer: "io unspecified" hatası giderildi. Android 9 ve sonrası şifresiz `http://` bağlantıyı engelliyordu; Nuvio'nun torrent ve yerel kaynakları bu yüzden açılmıyordu. Yerel sunucuların ilk baytı geç vermesi için bağlantı süreleri de uzatıldı.
- Telefon kumandası: aynı yöne art arda basışlar artık kaybolmuyor (tuşlar zaman damgasız gidiyor ve çift basış filtresine takılıyordu). Hava faresinden D-pad'e dönünce TV dokunma modundan çıkıyor ve odak geri geliyor. Gyro ve VR kaldırıldı.
- Ana sayfa: basılı tutunca raf filmler arasında akıcı ve eşit hızda kayıyor, kutu bilgileri bırakınca yükleniyor.
- DVD kutusu: açıkken sağ/sol bir sonraki filmin kutusunu açıyor. Fragman yuvası açılış ve kapanışta siyah kalmıyor, filmin sahne fotoğrafı görünüyor.
- Telefon Keşfet: Filmler/Diziler ayrımı, yeni öneriler için yenileme tuşu. Afişler kaydırınca kaybolmuyor (yalnızca yeni kartlar ekleniyor). Ailece, Arkadaşlar gibi çipler Gamze'nin adını aramaya katmadan yapay zekâ seçkisi getiriyor.
- GitHub Pages'teki telefon sayfası 1.0.0.20'den beri güncellenmemişti; CineDuel oradan da kalktı, CineQuiz zorluk ve sonsuz modu görünüyor.
- CineQuiz: kategori modu (Türk filmleri, Klasikler, Yeni filmler, Animasyon). İlk doğru cevaplayan %50, ikinci %25 fazla puan alıyor. Çeldiriciler aynı tür ve dönemden; orta zorluk daha az bilinen filmlerden. Afişler (oyuncu ve yönetmen adı yazdığı için) ve yazılı arka planlar yerine yazısız sahne kareleri; resim altında yıl yok.
- Gamze oyunlarda trivia söylüyor: yapay zekâ kotası bitince de filmin TMDB kaydından (slogan, yönetmen, bütçe/gişe).
- SnakePhile: kendi sinema salonu sahnesi (perdeler, film şeridi), piton desenli yılan, afişi ağzına alıp gövdesinden geçiren yutma animasyonu, yutulan filmlere dair notlar.
- CineRoulette: telefona sığıyor, "Yeniden oyna" çalışıyor, Gamze "güm/klik" demiyor (silah sesi oyun açılırken hazırlanıyor).
- Oyun ekranlarında imza ve "by Alptuğ Dağ".
- Ayarlar: Letterboxd sorusu ve kaydı için aç/kapat.
- AirPods paneli: D-pad ile tuşlar seçilebiliyor (sol tuş paneli kapatıyordu); odak seçili modda açılıyor.
- Arama: isteğin her kavramı birlikte aranıyor ("intikam alan kız babası" = intikam VE baba-kız; "emekli ajan" = emeklilik VE ajan).
- TV gerçekten kapanıyor: eski "hızlı açılış" ayarı TV'yi hazırda bekletmede tutuyordu. Artık yazılmıyor, daha önce yazılmışsa siliniyor; "CineTV'yi yok et" de siliyor.

## 1.0.0.21

1.0.0.20'nin dokuz betasındaki her şey (aşağıda) ve şunlar:

- Fragman, özet ve oyuncu fotoğrafları her filmde ve hemen geliyor: bağlantı bir an takılınca fragmanın "yok" diye unutulması kaldırıldı; özet ve oyuncular bir kez daha deneniyor; hızlı gezinmede birikmiş ön yüklemeler açılan kutuyu artık bekletmiyor.
- AlptugPlayer, visionOS düzeninde: oynat/duraklat ve 10 sn ileri/geri alt panelin solunda; ses parçası, kalite, görüntü oranı ve hız ikonları sağda; zaman çizgisi panelin içinde; film bitince saatin kaç olacağı ("Bitiş 23:41"); logo panelin üstünde küçük ve soluk, arayüzle birlikte beliriyor.
- AlptugPlayer TV'ye göre kendini ayarlıyor: donanım/yazılım kod çözücü (donanım bozuksa o kodek için yazılım), tünelli oynatma, tampon belleği TV'nin gücüne göre, ekran Dolby Vision göstermiyorsa baştan HDR10/SDR, güçlü TV'lerde asenkron kod çözme. Altyazılar TV'nin altyazı stilinde.
- Sarı tuş (CineSUB) AlptugPlayer'da: tünelli oynatmada görüntü ekran görüntüsüne düşmediği için oynatıcı önce tünelsiz moda geçiyor, CineSUB sonra bakıyor.
- Sistem merkezi TV'nin gerçek ayar sayfalarını açıyor: Google TV'de standart erişilebilirlik bağlantısı açılıp hemen kapanan boş bir sayfaydı, Bluetooth ve görüntü bağlantıları hiç yoktu.
- TV açılışında tuner bastırma Philips, TCL, Hisense, Sony, Xiaomi, Toshiba ve Vestel için de (yalnızca açılışın ilk 15 saniyesinde).
- Sürüm kodu: her sürüme on adım ayrıldı; 1.0.0.20 betalarını kuran TV'ler de 1.0.0.21'i alıyor.

## 1.0.0.20i · Beta 9

- TV açılışı: ekran açıldığı anda CineTV öne geliyor ve TV'nin kendiliğinden açtığı tuner/son HDMI girişi durduruluyor (eskiden pencere bir an görünüyordu).
- CineTV açılışı sakin ve kısa: nabız atışı kaldırıldı, yaklaşık 1,9 saniyeden 1,1 saniyeye indi.
- AlptugPlayer TV'ye göre kendini ayarlıyor: tünelli oynatma, TV'nin kod çözücüsü destekliyorsa açık; bu TV'de görüntüsüz kalırsa aynı saniyeden tünelsiz sürüyor ve bu TV için bir daha denemiyor.
- AlptugPlayer kontrolleri CineTV'deki gibi sıvı cam; "AlptugPlayer" yazısı küçük, yarı saydam ve yalnızca kontrollerle birlikte beliriyor.

## 1.0.0.20h · Beta 8

- AlptugPlayer: CineTV'nin oynatıcısı artık kendi kendini düzeltiyor. Tünelli oynatma hiç kullanılmıyor (birçok TV'de sesi verip görüntüyü vermiyor). Bir ayar çakışırsa (ses var görüntü yok, kod çözücü hatası) film aynı saniyeden bir sonraki profille sürüyor: Tam kalite → Dolby Vision kapalı → Güvenli mod (1080p, AVC, stereo). TV'de işe yarayan profil yayın türüne göre hatırlanıyor.
- Oynatıcıda CineTV yazı stiliyle "AlptugPlayer" yazıyor (kontrollerle ve filmin ilk saniyelerinde).

## 1.0.0.20g · Beta 7

- CineTV Oynatıcı: CineTV'nin kendi film oynatıcısı (ExoPlayer). Nuvio'nun harici oynatıcı seçeneğinde "CineTV Oynatıcı" seçilince filmler onunla açılır; Nuvio'nun ayarlarına CineTV dokunmaz.
  - HDR10, HDR10+, HLG ve Dolby Vision TV'nin kod çözücüsü destekliyorsa kendiliğinden; Dolby/DTS ses, HDMI destekliyorsa olduğu gibi aktarılır.
  - Ekran yenileme hızı filmin kare hızına eşlenir (23.976/24/25/50/60 Hz), çözünürlük değişmez.
  - HLS, DASH ve doğrudan dosyalar; Nuvio'nun gönderdiği altyazılar ve HTTP başlıkları; Türkçe ses ve altyazı öncelikli.
  - Kaldığı yerden devam eder ve konumu Nuvio'ya geri bildirir; sağ/sol 10 sn sarar, OK duraklatır; sarı tuşla CineSUB filmin adını bilerek çalışır.
- Arka planda gizlice üçüncü taraf oynatıcı (Nova) kurulmuyor; yerine CineTV Oynatıcı var.
- Ayarlar'da "Sistem" sütunu: Wi-Fi, Bluetooth, görüntü, ses, uygulamalar, depolama, tarih, dil, erişilebilirlik ve tüm sistem ayarları CineTV'den açılıyor (hiçbir sistem ayarı değiştirilmez).
- "CineTV'yi yok et": AirPods için değiştirilen surround ayarlarını da TV'nin ilk değerlerine döndürüyor; CineTV'nin varsayılan oynatıcı/asistan seçimleri kaldırılınca TV'ye geri dönüyor.
- CineTV açılış animasyonu TV beklemeden her açıldığında yeniden oynuyor (eskiden yalnızca ilk açılışta).

## 1.0.0.20f · Beta 6

- DVD kutusunun içi her temada belirgin biçimde değişiyor: gövde, tepsi ve kâğıt (ceviz kraft, kadife sinema, kırmızı halı, mavi cam, alüminyum, neon).
- Gamze odaktaki kutuyu biliyor ("onu aç", "buna benzer"); kota dolunca beklemeden yapay zekâsız aramaya geçiyor; istekte çocuk/aile/yaş geçince yetişkin, korku, gerilim, suç ve savaş türleri kodla çıkarılıyor.
- Film oynarken mikrofona "bu sahnede ne var?", "bu yemek ne?", "bu şarkı ne?" deyince Gamze filme dönüyor, CineSUB kareye bakıyor; CineSUB en iyi bulguyu Gamze'nin sesiyle söylüyor.
- Sistemin asistan, sesli komut ve uzun basılı arama girişleri Gamze'yi açıyor (CineTV seçildiğinde).
- Gamze zor sorgularda: "bir saatten kısa filmler" süreyi her filmin gerçek süresinden kodla denetliyor; "Almanya'da geçen Türk filmleri" dili ve mekânı ayrı ayrı arıyor; ödül ve festival sorgularında (kazanan, aday, yıl, kategori) yalnızca o yılın gerçek listesi.
- CineQuiz: Kolay / Orta / Zor / Sinefil zorlukları (film havuzu, şıkların yakınlığı ve Gamze'nin soruları seviyeye göre; doğru cevap 10/15/20/30 puan), süresiz sonsuz oyun (ev sahibi "Oyunu bitir" ile bitirir), üst üste doğrularda seri bonusu.

## 1.0.0.20e · Beta 5

- Oynat (DVD kutusu, telefon, oyunlar) yeniden doğrudan filmin Nuvio'daki kaynak sekmesini açıyor. TV'de denendi: Nuvio yalnızca nuvio://detail/... bağlantısını, o da yalnızca temiz açılışta tanıyor; CineTV filmin sayfasını açıp "Oynat" düğmesine kendisi basıyor (adıyla bulup; körlemesine OK değil).
- Vitrin yalnızca TV açılınca, 3 saatten uzun aradan sonra Ana Ekrana dönünce ve yenile tuşuyla yenileniyor; saat başı ve 45 dakikada bir kendiliğinden yenilenmesi kaldırıldı. Yenilenirken eski vitrinin üstünde "hazırlanıyor" yazmıyor. Yapay zekâ kotası dolduysa kategori vitrini beklemeden geliyor.

## 1.0.0.20d · Beta 4

- CineSUB: "çok fazla istek" aslında Gemini ücretsiz anahtarının günlük kotasıydı (Flash günde 20 istek). Raftaki her film için arka planda yazılan yapay zekâ özetleri bu kotayı bitiriyordu; özet artık yalnızca açılan kutu için yazılıyor, hızlı metin işleri Flash-Lite'a gidiyor. Kota dolunca ne zaman yenileneceği yazıyor; meşgul model bir sonrakine devrediliyor.
- CineSUB sol üstte artık eski filmin adını ("Oldboy") göstermiyor; yalnızca karede tanınan filmi yazıyor.
- Arama: "Emekli Ajan filmleri" gibi tema istekleri artık başlık araması değil; "vampirli filmler" konu odaklıları önce, vampirin geçtiği diğer filmleri sonra getiriyor; uzun tarifli istemler.
- AirPods paneli: uzamsal ses gerçek bir sanal surround efektine bağlandı (Kapalı / Sanal Surround / Geniş Sahne); işlevsiz gürültü denetimi seçenekleri kaldırıldı.

## 1.0.0.20c · Beta 3

- Oynat (kutu ya da telefon): Nuvio her seferinde temiz açılıp doğrudan filmin kaynaklarına gidiyor; ana ekranda ya da son filmde kalmıyor, film kaldığı yerden sürüyor.
- Letterboxd sorusu filmi artık hiç bekletmiyor: film hemen başlıyor, soru TV'ye dönünce en üstte soruluyor.
- AirPods: kulaklık bağlanınca paneli kendiliğinden açılıyor (Ayarlar'dan çıkarıldı); ses gece kendiliğinden %30'a inmiyor (sessiz saatler hatası).
- Gamze İngilizce adları ve kelimeleri İngilizce okunuşuyla söylüyor.
- "Ağ" düğmesiyle açılan Wi-Fi ayarları kapanmıyor.
- Vitrin yine tam sayıda kutu gösteriyor, kutular ekranın tam kenarından giriyor; odaktaki parlama yumuşadı; yansıma yavaşça beliriyor.
- DVD kutusu Geri ile, açılışın tam tersi bir animasyonla kapanıyor.
- Fragman kutuya odaklanınca yüklenmeye başlıyor; siyah kare yerine hemen fotoğraf, gecikirse "Yükleniyor...".
- Kaldırıldı: CineSUB öğreticisi, CineDuel, telefondaki arama altı çipleri, "vitrini beklemeden..." yazısı. Telefonda 3D Gyro ikinci basışta kapanıyor.
- Raflar daha hızlı hazırlanıyor ve ana göre temalı (gün, saat, ay, mevsim, hava, varsa şehir).
- Arama: içerik tarifinden (sahne, karakter, replik, nesne) film bulma; vitrinde kaydırdıkça yeni sonuçlar ekleniyor.
- CineSUB: kameolar, tarihi yapılar, sahnedeki müzik; çerçeve tam nesnenin etrafında, daha az ama daha iyi bilgi.
- SnakePhile: afişler görünüyor, yılan kenara çarpınca oyun bitiyor; telefondan SnakePhile açınca artık doğru oyun açılıyor.

## 1.0.0.20b · Beta 2

- Erişilebilirlik, cihaz ayarları ve dosya yöneticisi artık açılır açılmaz kapanmıyor (MediaTek TV menüleri tuner sanılmıyor).
- Kumandanın mikrofonu Google asistanı yerine Gamze'yi açıyor.
- "Kendini Yok Et" çalışıyor: kaldırma sırasında CineTV kendini geri getirmiyor.
- Oyundan çıkınca oyun menüsüne dönülüyor.
- Ayarlar açıkken arkadaki ana sayfa kaymıyor; Geri'ye basınca odak açılan öğeye dönüyor.
- Kumandada çift basışta odağın iki öğe atlaması engellendi.
- Rafı yenile her basışta başka tema ve filmler getiriyor.
- Nuvio'da kataloglar Geri ile kapanıyor (film oynarken Geri yine CineTV'ye döner).
- YouTube fragmanı çerçeveye tam sığıyor, büyütülmüyor.
- Oynat: Nuvio doğrudan filmin kaynaklarına gidiyor; IMDb kimliği eksikse önce bulunuyor; Nuvio soğuk açılırsa bağlantı yeniden veriliyor.
- Ekran koruyucu seçenekleri: süre (1-60 dk / hiçbir zaman), uygulamaların üzerinde açılma, önizleme.
- Klavyedeki film önerileri tam adlarıyla gösteriliyor.

## 1.0.0.20 · Büyük Güncelleme

Bu sürüm, 1.0.0.9'dan bu yana sürüm notlarına hiç yazılmamış her şeyi de kapsar (1.0.0.10–1.0.0.19 arasındaki yenilikler en altta).

### CineParty baştan yazıldı
- CineSUB altyazıları okuyup içlerindeki kavramları, deyimleri ve göndermeleri açıklıyor ("bu replikle Apocalypse Now'a gönderme yapıyor"); tablolarda eser adı, ressam ve yıl veriyor.
- Sarı tuş CineSUB'u artık Nuvio/YouTube üstünde gerçekten açıyor (CineTV'nin kendi katmanları ön plandaki uygulama sanılıyordu). CineSUB isimsiz kişileri işaretlemiyor, ekrandaki yemeği adıyla ve kökeniyle anlatıyor.
- Raflarda her filmin kendi logosu DVD sırtına basılıyor; odaktaki filmin sahnesi ana ekranda yumuşak bir ortam ışığı olarak beliriyor (zayıf GPU'larda kapalı).
- **Tek oyun motoru, iki ekran.** TV ortak sahneyi, her telefon o oyuncunun kendi ekranını gösterir. Telefonlar değişiklikleri anında alır (uzun yoklama); her telefon odanın yalnızca kendine düşen halini görür.
- **CineQuiz** (mor *Quiz*): süreyi oyuncular seçer (3–15 dk, soru başına 15–30 sn). Sorular TMDB'den gerçek görsellerle gelir: *bu sahne hangi filmden*, *bu oyuncunun oynadığı bir film yazın* (oyuncunun tüm filmleri kabul edilir), *bu filmde hangi oyuncu yok*, *yönetmeni kim*, *hangi yıl*; üstüne Gamze'nin kendi soruları ve her cevaptan sonra kısa bir bilgi notu. Yalnızca çok izlenmiş filmler sorulur. Herkes cevaplayınca (ya da süre bitince) cevaplar TV'de birlikte açılır, doğrular 10 puan. Süre bitince en çok bilen (eşitlikte en hızlı) şampiyon olur, telefonundan bu gecenin filmini seçer ve film Nuvio'da açılır.
- **CineRoulette** (mavi *Roulette*): altı haznede ana sayfadaki çerçevesiz afiş görünümüyle altı film, tek kurşun. Herkes telefonundan bir film yükler, boşları Gamze verdiğiniz ruh haline göre doldurur. Tetik sırayla çekilir: boş çıkan film elenir, kurşun bu gecenin filmini seçer. Süre her turda kısalır, süre biterse tetik kendiliğinden çekilir (−5); herkesin iki çevirme hakkı var; ilk iki tetik her zaman boştur.
- **CineSessiz** (altın *Sessiz*): süresiz sinema tabusu. Sırası gelen, salonun bileceği bir film, dizi ya da oyuncuyu kendisi seçer (aklına gelmezse Gamze önerir); Gamze beş yasaklı kelime koyar. Kart yalnızca anlatıcının telefonunda görünür (önceden tüm telefonlara gidiyordu). Tahminler yazım hatalarına, Türkçe ve orijinal adlara toleranslı. Takılınca Gamze şifreli bir ipucu verir.
- **Gamze sunuculuk yapar ve sözünü bitirmeden oyun ilerlemez;** soru süresi o soruyu okumayı bitirince başlar.
- **SnakePhile** geri geldi (bir sürümde yerini CineDuel'e bırakmıştı); **CineDuel** kendi yerinde, telefon nişangâh olur ve oyun bitince film artık kendiliğinden açılmaz.
- Oyunlar TV'de tek bir tuvalde çizilir; boştayken çizim yapmaz, zayıf TV'lerde arka plan animasyonsuzdur.

### CineSUB (turuncu *SUB*) her oynatıcının üstünde
- Nuvio'da, YouTube'da ve harici oynatıcıda **sarı tuş**: CineSUB kareyi yakalar, görsel yapay zekâ bilinmeye değer kişi, mekân, obje, yemek, tablo ve altyazıda geçen isim/yer/olayları konumlarıyla bulur. Konuların etrafında halkalar, yanlarında cam notlar belirir; notlar altyazıya değmez, ◀ ▶ aralarında gezer, OK daha fazlasını anlatır. Film durmaz.
- **Eşlik modu:** izlerken birkaç dakikada bir köşede tek bir kısa not (Ayarlar › Görünüm & Ses).
- Korumalı (DRM) içerikte uydurma bilgi yerine bunu söyler. Bilgi Gemini/Claude/OpenAI görsel modelleriyle; anahtar URL'ye yazılmaz.
- Altyazı ve Info tuşları artık oynatıcılardan çalınmıyor.

### Gamze sohbet ediyor
- Konuşmayı hatırlar (*“daha eskisi?”*), gerektiğinde tek bir soru sorar ve mikrofonu kendisi yeniden açar; kumandaya dokununca sohbet biter.
- **Gemini 3.8 Flash** (düşük düşünme, hızlı) ve yedek olarak 3.5 Flash-Lite.
- Çok spesifik isteklerde kurallar korunur: yaş, ülke, dönem; yıl aralığı kodla da uygulanır. *“Tom Hanks'in adada mahsur kaldığı film”* gibi sorular cevapla birlikte filmi de vitrine koyar.
- Hafıza artık boş alanlarla silinmiyor; adınızı, sevdiklerinizi ve sevmediklerinizi biriktirir.
- Ses cümle cümle akar; ilk cümle hazır olunca konuşmaya başlar, “Hemen bakıyorum” gibi cümleler önceden hazırlanır, kota dolan sunucu atlanır; asistan ses kanalı kullanılır.
- Açılış selamı kişisel ve kısa; hava durumu okunmuyor.

### Ses tasarımı
- Arayüz ve oyun sesleri yeniden sentezlendi: cam ve ahşap dokunuşlar, kutu açılışında alçak bir yükseliş, film başlarken derin bir vuruş; oyunlarda kalp atışı, tambur, horoz, boş tetik, kurşun, doğru/yanlış, alkış. Stereo, oda yankılı.
- Sistem sesi kapalı olan TV'lerde de duyulur (medya kanalı). Arayüz sesleri, oyun sesleri ve Gamze'nin oyun sunuculuğu ayarlardan ayrı ayrı kapatılabilir. Telefonlar da aynı sesleri çalar.

### Raf, fragman, kitapçık
- **Fragmanlar YouTube arayüzü olmadan:** yerel oynatıcı Android 11'de her seferinde çöküp YouTube oynatıcısına düşüyordu (NewPipe ↔ Java NIO); düzeltildi, NewPipe 0.26.5. Kadraja tam oturur (kırpma/büyütme yok), odaktayken önceden yüklenir, siyah açılış yerine afişten görüntüye geçer.
- **Tek basış = tek adım:** kumandaların kısa basışta gönderdiği tekrarlar artık iki film atlatmıyor.
- Odaktaki kutu kendi ekseninde belirgin biçimde döner ve durur; selofan kılıfta açıyla gezinen gerçekçi bir parlama ve üst kenar parlaklığı.
- Kitapçık: oyuncu portreleri geç kalırsa geldiğinde kitapçığa işlenir; öncül kısaltmalarda ya da kelime ortasında kesilmez; kısa öncüller sayfada dengeli durur; kâğıt temaya göre basılır (parşömen, kraft, altın varaklı program, buzlu akrilik, alüminyum, neon kart).

### Temalar ve arayüz
- Başlık her fotoğraflı arka planda okunur (üstte yumuşak gölge, raf başlığı cam kapsülde); Sıvı Cam damlaları saatin ve odağın üstünden çekildi; alt kenarda kesik görünen uygulama kutusu yumuşakça eriyor.
- Tüm temalarda yeni CineTV ikonu ve TV banner'ı; telefonda kutu içinde kutu görünmüyor, maskeli ikonlar artık sunuluyor.
- Açılış animasyonu 3,4 sn → 1,9 sn ve herhangi bir tuşla geçilebilir; tema sahneleri bir kez çizilip önbelleğe alınır.
- Tur: CineQuiz, CineRoulette, CineSessiz, CineSUB, eşlik modu, sohbet eden Gamze, ses tasarımı, akustik ve uzamsal ses kartları. *Yenilikler* ekranı bu sürümü anlatır.
- Uzamsal ses paneli (AirPods ayarlarından) yeniden erişilebilir.
- Düğmeli bazı pencerelerde (ör. Oda Akustiği) odak hiçbir düğmeye gitmiyordu ve OK çalışmıyordu; düzeltildi.

### Performans ve uyumluluk
- Ana ekran her karenin süresini ölçer; yavaşsa rafı kendiliğinden hafifletir (daha kısa raf, uzak kutularda bulanıklık ve kılıf yok, tek ışık katmanı), hızlanınca geri açar. Boştayken hiçbir şey çizmez (önceden odaktaki kutunun sonsuz salınımı bütün rafı her karede yeniden çizdiriyordu).
- Android 8'e kadar API denetimi; RAM temizleyicinin eksik izni eklendi; çökme riski taşıyan yerelleştirme dosyaları kaldırıldı.

### Güncelleme, kaldırma, geri bildirim
- **Otomatik güncelleme gerçekten çalışır:** indirilen APK CineTV'nin imzasını taşımalıdır; sessiz kurulum CineTV kapanırken de tamamlanır ve sonraki açılışta haber verilir; adb yoksa Android'in kurulum ekranı açılır. TV artık güncelleme bildirimi için gece uyandırılmıyor. Play Protect ve paket doğrulayıcı artık kapatılmıyor.
- **CineTV'yi Yok Et:** önce TV'nin kendi başlatıcısı geri açılır, tüm ayarlar geri alınır, CineTV'nin koyduğu dosyalar silinir; kaldırma sırasında hiçbir koruma CineTV'yi geri getiremez.
- **Geri bildirim döngüsü:** TV Denetçisi'ndeki rapor QR'ı hazır bir GitHub kaydı açar, cihaz uyumluluk listesine işlenir; `ANTHROPIC_API_KEY` tanımlıysa Claude Code sorunu inceleyip düzeltme PR'ı açar (kendiliğinden birleştirmez). Uyumluluk listesi yalnızca gerçekten denenmiş TV'leri içerir.
- Akıllı ev uç noktaları (Siri/Alexa/Google) artık eşleştirme anahtarı ister.
- Özel günler: Ramazan ve Kurban Bayramı (arife dahil), Anneler, Babalar, Öğretmenler ve Sevgililer Günü; Ramazan modu Diyanet tablosundaki yıllarda bir gün kaymıyor.

### 1.0.0.10 – 1.0.0.19 (sürüm notlarına yazılmamış olanlar)
- Gamze kimliği ve sesi (EMA-Lightning nöral Türkçe TTS), “Kiminle izliyorsun?” bağlamı, yerel hafıza, his/atmosfer araması (Vibe Seeker), benzer filmler.
- AirPods ve Bluetooth kulaklık entegrasyonu, uzamsal ses HUD'ı, Bluetooth mikrofonla sesli komut.
- Tek sayfa ayarlar ve iOS anahtarları, TV Denetçisi, Ambilight senkronu, gece izleme modu, renkli tuş atamaları (HDMI-CEC).
- Kırmızı Halı, Film Dolabı ve kadife perdeli Sinema Salonu temaları, HUD vizörü ipuçları, kitapçık filigranı.
- Telefon: PWA olarak kurulum, 3D jiroskop hava faresi, 16:9 akıllı duvar kâğıdı kırpma, ortak izleme listesi, Letterboxd günlüğü.
- Özel gün kutlamaları ve balonlar, Ramazan iftar/sahur sayacı, sessiz açılış güncelleyicisi, Windows masaüstü sürümü, Samsung Tizen paketi.

## 1.0.0.9

### Yenilikler & Düzeltmeler
- **Siri / visionOS Tarzı Kromatik Kenar Dalgaları (Siri Edge Waves)**:
  - Mikrofon tuşuna basıldığında ortada beliren basit mikrofon ikonu yerine ekranın tüm kenarlarını çevreleyen, mikrofondan gelen sesin RMS şiddetiyle dinamik dalgalanan akışkan kromatik neon dalgaları (Cyan, Magenta, Violet, Kehribar Sarısı) ve OLED derinliğinde ambiyans ışıması uygulandı.
  - Ekranın alt kısmına şeffaf Apple Intelligence tarzı zarif Liquid Glass hap kapsülü ve gerçek zamanlı transkripsiyon metni yerleştirildi.
- **DVD Kitapçığında Sabit Geometrik Düzen & Sıfır Çakışma Garantisi**:
  - Oyuncu portresi ve isimlerinin yan yana sıkışmasından kaynaklanan üst üste binme sorunu tamamen giderildi; dikey kart mimarisi ve sütun bazlı `clipRect` korumasıyla madalyon ve isimlerin asla birbirine taşmaması sağlandı.
  - Büyütülen dairesel madalyonlar (`u * 0.13f`), belirginleştirilen el yazısı isimler, film adı (`u * 0.082f`), 1-2 vurucu cümlelik tam özet (`u * 0.054f`) ve künye/puanlar her filmde birbirine denk, dengeli ve kusursuz editoryal oranlara kavuşturuldu.
- **Fragman Oynatıcıda Sıfır YouTube UI Görünümü (0.0s Flash-Free Playback)**:
  - Fragman başlarken YouTube video başlığı, logo ve oynat butonunun 1 saniye kadar görünmesi sorunu çözüldü.
  - Oynatıcı, YouTube IFrame API'sinden `PLAYING (state=1)` sinyali alana ve arayüz tamamen sönene kadar tam opak siyah perdede bekletilip ardından pürüzsüzce açılıyor. Ayrıca 36px taşma kırpmasıyla YouTube kenar başlıkları ve logoları tamamen kadraj dışına itildi.
- **Mobil Web Kumandası PWA / Ana Ekrana Ekleme Desteği**:
  - `manifest.webmanifest` göreceli yol (`.`) ile güncellendi; kumanda ana ekrana eklendiğinde oturum anahtarları `localStorage`, `sessionStorage` ve kalıcı çerezlerde (`cookie`) saklanarak oturum kaybı önlendi.
  - Yetki süresi dolduğunda veya sıfırdan açıldığında TV'de izin isteme penceresinin arka planda kalmaması için ana etkinlik (`MainActivity`) öne getirilerek televizyonda anında izin diyaloğunun belirmesi sağlandı.
- **Menü Tema Seçeneklerinde Eşit Boyut & Sol Kenar Kesilme Düzeltmesi**:
  - "Sinema Salonu" gibi uzun isimli butonların satırı genişletmesi önlendi; tüm tema butonlarına eşit ağırlık (`setWeightSum(2f)`), tek satır sınırlaması ve son satırdaki tekli buton için hizalama dengeleyicisi eklenerek tüm tema butonları milimetrik eşit boyuta getirildi.
  - Ayarlar paneli sol kenarına güvenli kenar boşluğu ve `clipChildren=false` eklenerek en soldaki butonların çerçeveye çarpıp kesilmesi engellendi.
- **Performans Optimizasyonu zRAM Metni Düzeltmesi**:
  - Ayarlar altındaki zRAM hakkındaki yanıltıcı metin düzeltildi; root gerektirmeden dinamik sanal bellek (zRAM) ve bellek yönetiminin devrede olduğu bilgisi güncellendi.
- **Alptuğflix Marka Bütünlüğü**:
  - Televizyonlardaki Nuvio güncellense veya harici APK kurulsa bile ana sayfadaki kart ve etiket daima Alptuğflix olarak kalacak şekilde kilitlendi; NuvioSetup resmi upstream Nuvio'yu doğrudan kuracak şekilde güncellendi.
- **Film Dolabı Raf Dokusu & Doğal Derinlik**:
  - Film Dolabı temasında vitrinin arkasını kaplayan rahatsız edici suni koyuluk ve koyu bant katmanı tamamen kaldırıldı; kütüphane dolabının orijinal ahşap/laminat derinliğini yansıtan gerçekçi mimari uygulandı.
- **Uygulama Tepsisi "Ekle" (+) Butonunda %100 Şeffaf Cam Uyumu**:
  - Tüm temalarda "Ekle" butonunun arkasındaki opak gri arka plan ve uyumsuz köşe yarıçapı kaldırıldı; Liquid Glass yarı şeffaf dumanlı kristal gövde (`createAddCard`), 22dp köşe yarıçapı ve platin odak ışıltısı eklendi.

## 1.0.0.8

### Yenilikler & Düzeltmeler
- **Fotogerçekçi Optik Medya (CD / DVD) & Sinematik Dönüş Fiziği**:
  - DVD diski 6 bölgeli otantik optik disk mimarisiyle baştan tasarlandı: Dış polikarbonat şeffaf pah halkası ve speküler yansıma, mikroskopik veri izleri (micro-grooves) içeren ipeksi serigrafi poster baskısı, lazer kazımalı IFPI matris barkod mikro metinleri (`IFPI L011 · CINETV MASTER DISC · DVD-9`) barındıran ayna lead-in halkası, 3D kabartmalı istifleme halkası (stacking ring) içeren şeffaf polikarbon göbek ve 15mm şeffaf merkez delik.
  - Optik diskin üzerine düşen ışık yansıması, disk döndükçe dinamik açı alan ve gerçek difraksiyon ızgarası fiziğine dayalı çift konili gökkuşağı saçılımı (opposing rainbow diffraction wedges) ile zenginleştirildi.
  - Film başlatma animasyonu gerçeğe uygun motor tork fiziğine kavuşturuldu: Üstel hızlanma eğrisi ($\theta(t) = t^{2.2} \times 2160^\circ$) ile önce tork alıp ardından yüksek devirli dönüş hissi veriyor; disk tepsiden ayrılırken hafif 3D eğimle (pitch tilt) yükselip ekrana yaklaşırken pürüzsüzce tam siyah perdeye geçiyor.
- **Kitapçık Oyuncu Portreleri & Özet İyileştirmeleri**:
  - Oyuncu fotoğraflarının arkasından taşan beyaz dikdörtgen kağıt fazlalıkları kaldırıldı; oyuncu portreleri zarif fildişi zemin halkası, gömme gölge ve dairesel çerçeveyle kusursuz madalyon görünümüne kavuşturuldu.
  - Kitapçık sayfasını tamamen kaplayan uzun film özetleri yerine, filmin ana temasını veren, spoiler içermeyen ve en fazla 1-2 cümlelik vurucu özetleme algoritması (`punchyOverview`) getirildi.
  - Oyuncu portreleri %65 daha büyük alana genişletildi (`castH` %48 büyütüldü); oyuncu isimleri ve fotoğrafları kitapçıkta çok daha belirgin ve okunaklı hale getirildi.
- **Doğrudan Fragman Oynatımı & Chromium CSP Güvenliği**:
  - Chromium WebView'daki `require-trusted-types-for 'script'` CSP kısıtlamasına takılan iç içe iframe yapısı kaldırıldı; doğrudan YouTube embed URL navigasyonuna geçildi.
  - WebView `domStorageEnabled`, `databaseEnabled` ve optimize TV User-Agent ile güçlendirildi.
  - Sayfa yüklenir yüklenmez video oynatımını tetikleyen ve 400 ms pürüzsüz kararma/açılma sağlayan `onPageFinished` entegrasyonu ve 1.6s güvenlik zaman aşımı koruması eklendi.

## 1.0.0.7

### Yenilikler & Düzeltmeler
- **DVD Kutusu Yatay Açılış & Fragman Oynatımı**:
  - Düşük RAM / Tier LOW televizyonlarda (Arçelik TV vb.) fragman oynatıcının donanım seviyesi kontrolü kaldırılarak tüm cihazlarda fragman oynatımı ve 90° yatay (landscape) kutu açılışı tamamen etkinleştirildi.
  - Fragman gösterimi tercihi varsayılan olarak açık (`true`) hale getirildi.
  - Fragman arama motoruna Cinemeta meta yedekleme sistemi eklendi; TMDB anahtarı girilmemiş olsa dahi Cinemeta üzerinden resmi YouTube fragman kimliği anında çekilerek kesintisiz oynatılıyor.
  - Fragman içi YouTube oynatıcı WebView altyapısı doğrudan ve pürüzsüz döngü (native seamless loop) ile optimize edildi; aralarda oynat/duraklat butonlarının ve yükleme ikonlarının belirip kaybolması engellendi.
  - Oynatıcı görünümüne siyah perde ve zaman aşımı güvenlik mekanizması eklenerek fragman yüklenirken takılı kalma veya siyah ekranda gizli kalma olasılığı sıfırlandı.
- **Yüksek Performans (En Güçlü Mod) Optimizasyonu**:
  - `PosterShelf` vitrin konteyneri üzerindeki gereksiz `LAYER_TYPE_HARDWARE` FBO katman tamponu kaldırılarak tüm temalarda doğrudan 60 FPS donanım çizimine geçildi; vitrin kaydırma ve 3D kutu animasyonları tereyağı akıcılığına kavuştu.
  - `SpatialEnvironment` arka planında `compositedBackdrop` ön-bileşik arka plan tamponu tüm performans profillerinde aktif hale getirildi; yüksek modda her karede gereksiz arka plan katmanlarının baştan çizilmesi önlendi.
  - Odak ışığındaki 3. aşırı geniş yayılım katmanı optimize edilerek GPU dolum hızı (fillrate) darboğazı giderildi.
  - `DvdOpeningView` animasyon döngüsü içerisindeki `Paint` nesne tahsisleri sınıf seviyesine taşınarak Garbage Collector duraklamaları önlendi.
  - `MemoryOptimizer` içerisindeki aşırı agresif `max_cached_processes 8` kısıtlaması kaldırılarak Android sistem servislerinin arka planda döngüsel olarak yeniden başlatılması ve CPU tüketimi engellendi.

## 1.0.0.6

### Yenilikler
- **Kırmızı Halı ve Sinema Salonu UI Ayrımı**:
  - *Sinema Salonu (Cinema Hall)*: Klasik 1930'lar Art-Deco sinema salonu atmosferi; derin bordo kadife malzeme (`MAT_VELVET`), antik pirinç/sıcak altın kordon bordürleri (`#E5A93C`), sıcak akkor flamanlı lamba aydınlatması.
  - *Kırmızı Halı (Red Carpet)*: Modern festival & Oscar gala prömiyeri; yepyeni `MAT_REDCARPET` malzemesi (safir yakut kırmızı degrade, smokin/black-tie derin kontrast), 24K parlak şampanya altın ve elmas beyazı ışık kenarları (`#FFE57F`), paparazi flaş parıltıları.
- **Kırmızı Halı Vitrin Başlığı Okunurluğu (Özel Kırmızı Tasarım)**:
  - Arka plandaki festival amblem/logosundan dolayı vitrin başlığının kaybolmasını önlemek için sadece Kırmızı Halı temasına özel olarak vitrin başlığı yüksek kontrastlı prömiyer kırmızısına (`#FF2638`) ve derin zemin gölgesine kavuşturuldu.
- **Bütün Temalarda %100 Kusursuz ve Eşit Düzen (Layout)**:
  - Kırmızı Halı temasındaki suni 38px'lik yükseltilmiş blok katmanı ve yan dikmeler kaldırıldı; DVD kutuları arka plandaki 3D kırmızı halı dokusuna organik kadife temas gölgesi ve flaş pırıltılarıyla doğrudan ve tüm diğer temalarla birebir aynı düşey eksende oturtuldu.
- **Her Temaya Özgü Karakteristik Ögeler**:
  - **Saat & Tarih**: Kırmızı Halı'da şampanya altın, Sinema Salonu'nda nostaljik art-deco altın/fildişi, Neon Gece'de lazer pembe/camgöbeği, Metalik'te platin gümüşü, Sıvı Cam'da buz mavisi kristal, Film Dolabı'nda stüdyo beyazı.
  - **Alptuğ Dağ İmzası & Wordmark**: Her temanın ışığına göre özel renk degrade veya metal/altın/kristal tonlu imza ve tema renginde yazar künyesi.
  - **DVD Kutularının Dış Görünüşü & Işık Derecelendirmesi**: Her odaya özel GPU donanım renk matrisi (`grade`); Kırmızı Halı'da spot ışıklı yakut ışıltısı, Sinema Salonu'nda akkor tungsten sıcaklığı, Metalik'te platin gümüş yansımaları, Sıvı Cam'da kristal kırılımları.
  - **Arama Çubuğu**: Tema malzemesiyle senkronize cam hazne, temaya özel büyüteç ikonu, ipucu rengi ve mikrofon ikonu.
  - **UI Butonları**: Başlık butonları, odak ışıkları ve kontrol kapsülleri temasının vurgu rengine ve malzemesine tam duyarlı.

## 1.0.0.5

### Yenilikler
- **Kırmızı Halı (Red Carpet) Teması**: Ödül töreni atmosferini yansıtan peluş kırmızı kadife halı (`#CF1427`), çift altın saçak kordon şeritleri, 3D küresel başlıklı altın kordon dikmeleri (stanchions), tavan projektör spot ışıkları ve parıltılı paparazi flaş patlamalarıyla donatılmış yepyeni bir sinematik tema eklendi. Eski OLED teması geriye dönük tam uyumlulukla bu temaya yükseltildi.
- **Sinema Salonu Teması Perde Hizalaması & Aydınlatma**: Kadife proscenium perdeleri cihazın ekran sınırlarını dinamik piksel seviyesinde hesaplayarak (`leftBleed`, `rightBleed`) ekranın sol ve sağ kenarına sıfır boşlukla (flush) dayandı; vitrin başlığının perdelerin arkasında kalması sorunu ve aydınlatma artefaktları tamamen düzeltildi.
- **UI Artist Tema Cilalama (Tüm 6 Temada)**:
  - *Alptug Teması*: Odak ışığı arkasına ultra-yumuşak ortam difüzyon katmanı ve 4K imza kontrast ayarı.
  - *Sıvı Cam (Liquid Glass)*: Düşük donanımlı cihazlarda gölgeleri koruyan ve performans kaybettirmeyen hafif alfa zemin gölgesi ve 45 derecelik tutarlı ışık açısı.
  - *Metalik (Metallic)*: Çözünürlüğe duyarlı gömme cıvata ve oyuk yarıçapları.
  - *Film Dolabı (Closet)*: Sıcak fildişi/krem (`#FFF8F0`) pah parıltısı ve derinleştirilmiş köşe vinyetleri.
  - *Neon Gece (Neon Night)*: Pürüzsüz antialias güneş dilimi aralıkları.
  - *Kırmızı Halı (Red Carpet)*: Altın kordon ve dikmeler üzerinde yıldız biçimli paparazi flaş parıltıları.
- **Sinematik Açılış Animasyonu (Intro Splash)**: Sarı açılış ışığı yerine logonun kırmızı tonlarıyla kusursuz uyumlu, kalp atışı gibi organik zonklayan kırmızı sinematik glow; DVD kutularındaki parlama efekti gibi logonun üzerinden geçen zarif ışıltı dalgası.
- **DVD Kutusu Fragman Sekmesi İyileştirmeleri**: Fragman sekmesinde "Fragman" başlığı yüksek kontrastlı beyaz renge çekildi. 16:9 fragman videosunun çerçeveye tam oturması sağlandı; video oynarken YouTube kontrollerinin anlık belirip kaybolması opak geçiş perdesiyle tamamen maskelendi.
- **OEM Tuner & Canlı TV Bastırma**: Televizyon açılışlarında veya CineTV'ye geçişte OEM TV tuner'ının (uydu/anten canlı yayın arayüzü) CineTV'nin önüne geçerek ekranı çalmaya çalışması arka plan tuner focus dismiss mekanizması ile engellendi.
- **Mobil Kumanda Safari/Chrome "Ana Ekrana Ekle" PWA İzin Yönetimi**: PWA olarak ana ekrana eklenen web kumandasının TV izin diyaloğunu tetikleyememesi sorunu çözüldü; izin isteği doğrudan TV WebRemoteServer üzerinden senkronize diyalog olarak televizyona düşürülür.
- **Arçelik TV zRAM ve RAM Optimizasyonu**: Arçelik TV'de zRAM / Swap sanal bellek alanının aktifliği güvenceye alındı; bellek temizliği ve arka plan bellek tüketimi stabilize edildi.
- **Alptugflix Dahili Logo Büyütme**: Alptugflix (Nuvio) içindeki tüm logo, başlık ve banner öğeleri sınırları aşmadan, tasarımı bozmayacak en yüksek ölçeğe büyütüldü.

### Çözülenler
- **Vitrin ve Perde Örtüşmesi**: Vitrin başlığı ve yan proscenium perdelerinin çakışması katman sırası ve bleed hesaplamasıyla tamamen giderildi.
- **Fragman Kontrol Titremesi**: YouTube oynatıcı kontrollerinin video başlarken sebep olduğu UI titremesi çift katmanlı perdeleme ve optik kırpma ile sıfırlandı.

## 1.0.0.4

### Yenilikler
- **Dahili Kurulum Sihirbazı (Setup Wizard)**: CineTV artık yanınızda bilgisayar veya AI ajanı olmadan, tek başına APK olarak kurulduğunda bile %100 eksiksiz ve kendi kendine yeten bir sistem kurar. İlk açılışta açılan sihirbaz (veya Ayarlar › TV & Telefon › Kurulum Sihirbazı); Varsayılan Başlatıcı, Nuvio Medya Merkezi & Türkçe Eklentiler, Video Oynatıcı, Kumanda Tuş Servisi ve 2 GB Sanal RAM bileşenlerini kontrol eder; tek tıkla "Tümünü Otomatik Tamamla" diyerek kumandayla her şeyi tamamlar.
- **DVD Kutusu Açık Halde Yakınlaşma & Büyütülmüş Tipografi**: DVD kutusunun açık hali koltuktan rahatça okunabilmesi için ekrana belirgin şekilde yaklaştırıldı ve %35 daha büyük ölçeklendi. İç kapaktaki film özetleri, el yazısı künye, yönetmen, süre ve posta pulu fotoğraflı başrol oyuncuları artık uzaktan çok net ve sinematik okunur. Kutu altına eklenen yumuşak 3D ortam temas gölgesiyle fiziksel derinlik hissi güçlendirildi.
- **Kusursuz Fragman Deneyimi & YouTube UI Parıltısının Engellenmesi**: Fragman oynatma sekmesinde YouTube'un başlık çubuğu, kanal logosu ve kontrollerinin açılışta anlık görünmesi 1.48x optik kırpma ve opak siyah perde (`#curtain`) geçişi ile tamamen engellendi. TMDB `details()` çağrısına videolar dahil edilerek fragman açılış gecikmesi sıfırlandı. Açık DVD tepsisinin üzerine film özetiyle birebir uyumlu el yazısı ve film mürekkebiyle **"Fragman"** başlığı eklendi.
- **Modern Dahili Sesli Arama Arayüzü & Yeşil Nokta (Privacy Indicator) Çözümü**: CineTV'nin VisionOS arayüzü ile uyumlu, buzlu cam ve sese duyarlı gerçek zamanlı akustik ses dalgaları barındıran modern bir sesli arama arayüzü (`VoiceOverlay`) geliştirildi. Dinleme tamamlandığında veya kapatıldığında ses donanımı hemen serbest bırakılarak Android 12, 13 ve 14'te ekranın köşesinde takılı kalan yeşil mikrofon noktası tamamen giderildi.
- **Profesyonel UI Tasarımı & Performans Kademesine (Tier) Göre Tema Optimizasyonu**:
  - *Sıvı Cam (Liquid Glass)*: Kostik ışık yansımaları, derinlik zemin gölgesi ve kromatizma safir saçılımlı akışkan damlacıklar; Low kademede 3 çekirdek damla ile hafif, High kademede tam optik kırılımlarla çalışır.
  - *Metalik (Metallic)*: Havacılık sınıfı fırçalanmış titanyum kaplama, anizotropik çapraz ışık parıltısı, 1px pahlı lazer dikişleri ve derinliği olan gömme cıvatalar; Low kademede sıfır döngü ek yüküyle yüksek performans.
  - *Neon Gece (Neon Night)*: Derin synthwave alacakaranlık göğü, yumuşak korona auralı parlayan retro güneş, atmosferik mesafe sisi ve sonsuz perspektif siber-ızgara.
  - *Film Dolabı (Closet)*: Ahşap dolap dokusu ve zemin gren döngüsü performans kademesine göre ölçeklendi; düşük donanımlı TV'lerde açılış hızlandırıldı.
- **Android 9 ve 14 Çekirdek Başlatıcı ve Erişilebilirlik Uyumluluğu**: Üretici arayüzleri tarafından gizlenen erişilebilirlik ve varsayılan başlatıcı menüleri CineTV'nin doğrudan sistem intentleri ile atlatıldı. `BootReceiver` entegrasyonu sayesinde televizyon fişten çekilip açılsa dahi CineTV arka planda otomatik uyanır.

### Çözülenler
- **Mobil Kumanda Klavyesinde Enter / Arama**: Telefondan web kumandası kullanılırken klavyede Enter/Ara tuşuna basıldığında aramanın başlamaması sorunu giderildi; form gönderimi ve Enter tuş kodları (key, keyCode, which) dinlenerek hem TV'de hem telefonda arama anında tetiklenir.
- **Downloader / Manuel Kurulum Eksiklikleri**: Yerel ADB portu kapalı veya yetkisiz olsa dahi Nuvio ve Nova Player APK'larının otomatik sistem paket yükleyicisine güvenli şekilde düşmesi (fallback) sağlandı.

## 1.0.0.3

### Yenilikler
- **Genişletilmiş TV sesli komutları & akıllı mikrofon tuşu**: Televizyonda hangi ekranda veya uygulamada olunursa olunsun, kumandadan mikrofon tuşuna basıldığında artık ana ekrana fırlatma veya menüye atma yaşanmaz; yalnızca oynatılan içerik duraklatılır ve pencere hemen kapanır. Sesli komutlara RAM temizliği ("ram temizle", "hızlandır", "belleği temizle"), televizyonu yeniden başlatma ("yeniden başlat"), altyazı ve ses dili/dublaj değiştirme, TV rehberi/EPG, menü ve popüler platform kısayolları (BluTV, Exxen, GAİN, TOD/beIN, Tivibu, Turkcell TV+, Disney+, Stremio) eklendi.
- **Gerçek iframe Referer ve Origin ile akış başlatma**: Yerli film sitelerindeki (vidmoly, closeload, rapidrame vb.) iframe içi CDN korumalı .m3u8/.mp4 akışları artık oynatıcıya (Nova, Just Player vb.) gerçek iframe Referer, Origin ve çerez bilgileriyle iletilir; daha önce "bağlantı hatası" veren yayınlar sorunsuz başlar.
- **Kutu içinde süre ve pul tasarımlı başrol oyuncuları (sıfır ek ağ isteği)**: DVD kutuları açıldığında ek hiçbir API isteği atmadan filmin ve dizinin süresi ("2s 28dk"), ilk 3 başrol oyuncusunun fotoğrafları vintage posta pulu ve damga mühür çizgileriyle el yazısı künyeye eklendi. Özet metni taşmayacak ve yarım kalmayacak şekilde dinamik ölçeklenir.
- **Diziler için logo ve slogan (tagline)**: Dizi kutularında da tipografik logo ve sloganlar eksiksiz görünür; medya türüne göre (film / dizi) doğru TMDB verisi çekilir.
- **Google Asistanı devre dışı bırakma & root'suz sanal RAM (zRAM/Swap)**: Televizyonu yavaşlatan Google Asistan devre dışı bırakılırken Google ses tanıma servisi açık tutulur. Root gerektirmeyen sanal RAM/zRAM genişletmesi arka planda uygulanır ve Ayarlar'daki *Optimize Et* butonu ile sesli komutlarla senkronize çalışır.
- **Ortalanmış ve görkemli açılış (loading) animasyonu**: Sola yaslı eski açılış kaldırıldı; ekranın tam ortasında radyal altın ışık aurası, parlayan CineTV logosu ve yükselen altın renkli *"by ALPTUĞ DAĞ"* imzası ile sinematik bir açılış inşa edildi.
- **Kusursuz ve seamless temalar (Film Dolabı & Sinema Salonu)**: Rafların altındaki yapay dikdörtgen bloklar ve halatlar kaldırıldı; ahşap dolapta ortam karartması ve temas gölgeleri, sinema salonunda sahne ışığı zemin yansımaları ve kadife drape gölgeleriyle zemin arka planla organik bir bütün oluşturuldu.
- **Türk kullanıcılar için hazır Nuvio eklenti paketi**: Nuvio kurulu olmayan Türk kullanıcılara Nuvio ilk açılışta Xperience, Netflix Kataloğu, Anthology, TR-Altyazı eklentileri ve hazır Türkçe AIOMetadata ayarlarıyla birlikte kurulur.
- **Oynatıcı hazır gelir**: televizyonda video oynatıcı uygulaması yoksa CineTV ilk açılışta Nova Video Player'ı (resmî sürüm, işlemciye uygun) kendisi kurar. Nova uygulama listesinde görünmez; yalnızca telefondan bir video gönderildiğinde açılır. Bir oynatıcı zaten varsa hiçbir şey kurulmaz.
- **Daha geniş yayın görünümü**: *Yayınla* sekmesindeki site görünümü artık telefonun bütün genişliğini kaplıyor; televizyondaki sayfa telefonun ekran oranında açıldığı için görüntü uzamıyor, dokunuşlar tam yerine gidiyor.

### Çözülenler
- **Mobil web kumanda klavyesi**: Telefondan yazarken harflerin tekrarlanması, öneri kelimesi seçince silinmeme ve arama yapmama sorunları diff tabanlı artımlı yazma ile giderildi; Enter tuşu aramayı tetikler; TV'de bir arama kutusuna odaklanıldığında telefon klavyesi otomatik açılır.
- **DVD kutularındaki Türkçe harf (ü, ğ) fontu**: El yazısı fontundaki eksik birleştirici işaretler tamamlandı ve Unicode NFC normalizasyonu uygulandı; Türkçe harflerin sistem sans-serif fontuna düşmesi engellendi.
- Uygulama tepsisi temadan temaya değişiyordu (bazılarında satırda 4, bazılarında 5 uygulama; "Ekle" kimi zaman alt satıra düşüyordu). Artık her temada aynı: satırda 5.
- Uygulama kutucukları yamuk duruyordu: eğim, satırda olmayan kutucuklar da varmış gibi hesaplanıyordu. Artık her satırın eğimi o satırdaki kutucuklara göre, iki uçta eşit; Uzamsal HUD'un kavisi aynı güçte.
- OLED Siyah'ta paneller ve kutucuklar zemine karışıp ekran simsiyah görünebiliyordu: artık zeminden bir ton açık, kenarları belirgin.
- Yayınla sekmesinde sayfayı parmakla biraz kaydırınca sayfa en alta (ya da en üste) fırlıyordu: kaydırma televizyona çok hızlı bir fiske gibi gidiyordu. Artık sayfa parmağın gittiği kadar, kaymadan kayıyor.
- Telefon sayfasını ana ekrana eklerken "File not found" hatası çıkıyordu (ana ekran kısayolu yanlış adrese gidiyordu). Artık kısayol doğru sayfayı açar ve televizyonu hatırlar; QR'ı yeniden okutmak gerekmez.

### Bilinen hatalar
- Nova'nın otomatik kurulumu ve geniş yayın görünümü gerçek kullanımda denenmedi.
- 1.0.0.2'deki bilinen hatalar sürüyor.

## 1.0.0.2

### Yenilikler
- **Telefon bağlantısı ev ağından bağımsız**: televizyondaki QR kod artık telefon sayfasını GitHub'dan (https) açar; telefon televizyona doğrudan bir bağlantıyla (WebRTC) ulaşır. Yerel IP adresleri, misafir ağları, telefondaki VPN'ler (ör. Samsung "Güvenli Wi-Fi") ya da tarayıcının "güvenli değil" engelleri artık bağlantıyı bozmaz; mikrofon ve hava faresi her tarayıcıda çalışır. Yerel adres, aynı Wi-Fi'deki telefonlar için QR'ın altında yazılı kalır.
- **Gezinerek yayınlama**: telefondaki *Yayınla* sekmesinde film sitesi telefonda açılır (sayfayı televizyon görünmeden yükler, telefon canlı görür). Reklamları kendiniz geçip filmin oynat tuşuna basınca film televizyonda başlar. 3 dakikadan kısa videolar (reklamlar) hiç gönderilmez; sayfa televizyonda ses çıkarmaz. Çerçeve içindeki oynatıcılar kendiliğinden ayrıca açılır.
- **Nuvio hazır gelir**: CineTV yalnızca APK ile kurulduysa ilk açılışta Nuvio'yu (işlemciye uygun resmî sürüm) ve temel eklentileri kendisi kurar. Nuvio zaten varsa hiç dokunulmaz.
- **Hep koyu**: açık / koyu mod kaldırıldı; bütün temalar koyu.

### Çözülenler
- Fragman anları: fragman oynarken oynatıcının düğmeleri görünebiliyor ve fragmana dokunulup duraklatılabiliyordu. Artık fragman kilitli: kendiliğinden oynar, hiçbir dokunuş, tuş ya da imleç ona ulaşmaz, duraklarsa kendiliğinden devam eder.
- Telefon kumandası bazı televizyonlarda "siteye ulaşılamıyor" diyordu: QR kod, televizyonun Chromecast / Miracast için açtığı ikinci Wi-Fi'nin adresini gösterebiliyordu. Artık bağlı olunan ağın adresi kullanılıyor; QR'ın altında "açılmıyorsa" ipucu var.
- Fragman anları açıkken DVD'nin iç kapağında yönetmen, puanlar ya da başlık sınırdan taşabiliyordu: yazılar artık sığana kadar küçülüyor, gerekirse kısaltılıyor ve kâğıdın dışına hiç çıkmıyor.
- Yayınlanan HLS akışları (master.txt gibi) oynatıcıya yanlış türle gidiyordu.
- Uygulamanın içindeki ZXing ve yazı tipi lisans metinleri eksikti; artık uygulamayla ve depoyla birlikte geliyor (`licenses/`).

### Bilinen hatalar
- Gezinerek yayınlama ve Nuvio'nun otomatik kurulumu henüz gerçek kullanımda denenmedi (sitede filmin akışı bulunup gönderildiği doğrulandı; televizyondaki oynatıcının açılması son düzeltmeden sonra denenmedi).
- Bazı film sitelerinin akışları yalnızca kendi sayfalarından açılıyor; televizyonun oynatıcısı (Nova) bunu desteklemiyorsa film başlamayabilir. Listedeki diğer akışları deneyin.
- Hava faresi ve telefondan sesli komut, tarayıcının güvenli (https) sayfası gerektirir; düz adreste bazı tarayıcılar izin vermez.
- Fragman anları TMDB anahtarı ister; bazı fragmanlar YouTube dışında gösterilemiyor, o zaman tepsi boş kalır.

## 1.0.0.1
- Telefon kumandası tek ekranda (dokunmatik yüzey, hava faresi), gerçek kapatma, YouTube'dan "TV'de izle", sesli komut ve arama, fragman anları, ilk açılış turu, birbirinden farklı temalar (Sinema Salonu gerçek fotoğrafla, Film Dolabı), Düşük / Orta / Yüksek uyarlanan performans, Türkçe başlıklar ve orijinal adlar, tek uygulama listesi, kurulum rehberi (KURULUM.md), kaynak kodu koruyan lisans.
