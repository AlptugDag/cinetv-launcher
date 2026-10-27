# Sürüm notları · Release notes

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
