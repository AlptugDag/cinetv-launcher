# Sürüm notları · Release notes

## 1.0.0.3

### Yenilikler
- **Oynatıcı hazır gelir**: televizyonda video oynatıcı uygulaması yoksa CineTV ilk açılışta Nova Video Player'ı (resmî sürüm, işlemciye uygun) kendisi kurar. Nova uygulama listesinde görünmez; yalnızca telefondan bir video gönderildiğinde açılır. Bir oynatıcı zaten varsa hiçbir şey kurulmaz.
- **Daha geniş yayın görünümü**: *Yayınla* sekmesindeki site görünümü artık telefonun bütün genişliğini kaplıyor; televizyondaki sayfa telefonun ekran oranında açıldığı için görüntü uzamıyor, dokunuşlar tam yerine gidiyor.

### Çözülenler
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
