# Sürüm notları · Release notes

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
- **Türk kullanıcılar için hazır Nuvio eklenti paketi**: Nuvio kurulu olmayan Türk kullanıcılara Nuvio ilk açılışta Xperience, Netflix Kataloğu, AIOStreams Nightly, Anthology, TR-Altyazı eklentileri ve hazır Türkçe AIOMetadata ayarlarıyla birlikte kurulur.
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
