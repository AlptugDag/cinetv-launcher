# CineTV kurulum rehberi

Bu rehber hiç bilgisayar bilmeyen biri için yazıldı. Her adımı sırayla yapın; bir adımda takılırsanız en
alttaki **Sorun çözme** tablosuna bakın. *(English: [the short version is in the README](README.md#install-in-one-command).)*

**İndirme bağlantısı (her zaman en son sürüm):**
`https://github.com/AlptugDag/cinetv-launcher/releases/latest/download/CineTV.apk`

---

## Hangi yolu seçmeliyim?

CineTV'yi televizyonunuza kurmak için iki resmî ve garantili yol vardır:

| Elinizde ne var? | Yol | Süre | Kurulum şekli |
| :--- | :--- | :--- | :--- |
| Sadece televizyon ve kumandası | [1. Yol: Kurulum Sihirbazı (Önerilen)](#1-yol-kurulum-sihirbazı-önerilen--bilgisayar-gerekmez) | 3 dk | **TV kumandasıyla tek tıkla %100 eksiksiz ve otomatik** |
| Claude, Antigravity, Gemini gibi bir AI ajanı | [2. Yol: Yapay Zekâya Kurdur](#2-yol-bir-yapay-zekâya-kurdurun-kişiye-özel) | 10 dk | **Tamamen zevklerinize göre özel profil ve katalog rafları** |

---

## Adım 1: Televizyonda "Geliştirici seçenekleri"ni açın (her iki yol için de gerekli)

CineTV, kumanda tuşlarını her uygulamada çalıştırmak, kendini varsayılan ana ekran yapmak, Google Asistan çakışmasını engellemek ve arka planda 2 GB sanal RAM oluşturup sistemi hızlandırmak için televizyonun standart "hata ayıklama" özelliğini kullanır. Bu işlem televizyonun garantisini bozmaz ve istediğiniz zaman Ayarlar'daki **"CineTV'yi yok et!"** tuşuyla tamamen geri alınabilir.

**1.1. Geliştirici olun.** Menü adları markaya göre biraz değişir; size uyan satırı izleyin:

| Televizyon | Gideceğiniz yer | Ne yapacaksınız |
| :--- | :--- | :--- |
| **Android TV** (Arçelik, Beko, Grundig eski, Sony, Philips, TCL eski…) | Ayarlar › Cihaz Tercihleri › Hakkında › **Yapı** (Build) | **Yapı** satırının üstüne gelip kumandada **OK**'a **7 kez** basın |
| **Google TV** (Grundig yeni, TCL yeni, Chromecast, Hisense…) | Ayarlar › Sistem › Hakkında › **Android TV OS derlemesi** | Aynı satıra **7 kez OK** |

Ekranda *"Artık bir geliştiricisiniz"* yazısı çıkar. (Şifre sorarsa televizyonun kilit PIN'ini girin.)

**1.2. Hata ayıklamayı açın.** Bir önceki menüye dönün; artık **Geliştirici seçenekleri** var:

- Android TV: Ayarlar › Cihaz Tercihleri › **Geliştirici seçenekleri**
- Google TV: Ayarlar › Sistem › **Geliştirici seçenekleri**

İçinde şunları **Açık** yapın (hangisi varsa):
- **USB hata ayıklama**
- **Ağ hata ayıklama** (ya da *ADB over network* / *Kablosuz hata ayıklama*)

> Telefonunuz ve bilgisayarınız televizyonla **aynı Wi-Fi'ye** bağlı olmalı.

---

## 1. Yol: Kurulum Sihirbazı (Önerilen — Bilgisayar gerekmez)

CineTV v1.0.0.4 ile birlikte gelen dahili **Kurulum Sihirbazı** sayesinde, yanınızda bilgisayar veya harici bir araç olmasa dahi tek başına APK olarak kurulduğunda eksik kalan hiçbir şey olmaz.

1. Televizyonda **Play Store**'u açın, **Downloader** diye aratın (turuncu simgeli, *AFTVnews* yapımı) ve **Yükle**'ye basın. (Ya da `CineTV.apk`'yı bilgisayardan bir USB belleğe atıp televizyonunuza takabilirsiniz.)
2. Downloader'ı açın. Depolama izni isterse **İzin ver** deyin.
3. Bilinmeyen kaynaklara izin verin (Downloader ilk kurulumda kendisi sorar; sormazsa):
   - Android TV: Ayarlar › Cihaz Tercihleri › Güvenlik ve kısıtlamalar › **Bilinmeyen kaynaklar** › Downloader: **Açık**
   - Google TV: Ayarlar › Uygulamalar › Güvenlik ve kısıtlamalar › **Bilinmeyen kaynaklar** › Downloader: **Açık**
4. Downloader'ın ana ekranındaki adres kutusuna kumandayla şunu yazın, sonra **Go**:
   ```
   github.com/AlptugDag/cinetv-launcher/releases/latest/download/CineTV.apk
   ```
   (Büyük-küçük harfe dikkat: `AlptugDag` ve `CineTV.apk`.)
5. İndirme bitince **Yükle** (Install), sonra **Aç** (Open).
6. Televizyon *"USB hata ayıklamaya izin verilsin mi?"* diye sorarsa: **"Bu bilgisayardan her zaman izin ver"** kutusunu işaretleyin ve **Tamam**'a basın.
7. **CineTV Kurulum Sihirbazı**: CineTV ilk açılışında otomatik olarak karşınıza çıkar.
   - Sihirbaz ekrandaki 5 temel bileşeni gösterir:
     1. **Varsayılan Ana Ekran**: Kumanda Home (Ev) tuşu için sistem seçiciyi açar (*CineTV* › *Her zaman* seçin).
     2. **Alptuğflix Medya Merkezi**: Televizyonda medya merkezi yoksa arka planda Alptuğflix'i kurar ve hazır Türkçe film/dizi kaynaklarını, altyazı eklentilerini (Xperience, Netflix Kataloğu, AIOStreams Nightly, Anthology, TR-Altyazı) yükler.
     3. **Video Oynatıcı**: Yayınları oynatmak için Nova Player'ı kurar.
     4. **Kumanda Tuş Servisi**: Kumanda tuşlarının her ekranda çalışması için erişilebilirlik ayarını açar.
     5. **2 GB Sanal RAM & Optimizasyon**: Root gerekmeden 2 GB sanal RAM (zRAM) oluşturur ve Google Asistan'ı sessize alıp sistemi hızlandırır.
   - Kumandanızla **"Tümünü Otomatik Tamamla"** butonuna basarak her şeyi arka planda saniyeler içinde tamamlayabilir veya maddelere tek tek basarak ayarlayabilirsiniz.
   - Tüm göstergeler yeşile döndüğünde **"Tamamla"** butonuna basarak ana ekrana dönebilirsiniz.

> Bu sihirbaza daha sonra dilediğiniz zaman CineTV › Ayarlar › TV & Telefon › **Kurulum Sihirbazı** üzerinden de ulaşabilirsiniz.

---

## 2. Yol: Bir yapay zekâya kurdurun (Kişiye özel)

Bilgisayarınızda dosya okuyup komut çalıştırabilen bir yapay zekâ olmalı: **Claude Desktop** (Claude Code), **Antigravity** (ücretsiz), **Gemini CLI**, **Cursor**, **Codex** gibi. Tarayıcıdaki sohbet pencereleri (sadece yazışan) televizyona bağlanamaz.

1. Adım 1'i yapın, televizyonun IP adresini not edin (Ayarlar › Ağ ve İnternet › bağlı olduğunuz Wi-Fi › IP adresi).
2. Yapay zekâya aynen şunu yazın:
   > https://github.com/AlptugDag/cinetv-launcher adresindeki projeyi indir, AGENTS.md dosyasını baştan sona oku ve oradaki adımlarla CineTV'yi televizyonuma kur. Televizyonumun IP adresi 192.168.1.50. Bana soruları Türkçe sor.
3. Yapay zekâ size soru sorar (ne izlediğiniz, dil, servisler…), cevaplayın. Televizyonda izin sorusu çıkınca **Her zaman izin ver** › **Tamam**.
4. Komut çalıştırmak için onay isterse **İzin ver** deyin.

Yapay zekâ Alptuğflix'teki son adımları da (zevklerinize özel raflar, eklentiler, altyazı) televizyonun ekranına bakarak kendisi yapar.

---

### Geliştiriciler & İleri Düzey Kullanıcılar İçin: Terminal (İsteğe Bağlı)

```bash
git clone https://github.com/AlptugDag/cinetv-launcher
cd cinetv-launcher
./install.sh --tv 192.168.1.50
```

`adb` yoksa: Mac `brew install android-platform-tools`, Ubuntu `sudo apt install adb`. Anahtarlar isteğe bağlı:
`CINETV_TMDB_KEY=... CINETV_AI_KEY=... ./install.sh --tv 192.168.1.50`. Ayrıntılar: [AGENTS.md](AGENTS.md).

---

## Kurulumdan sonra

- **Telefon kumandası**: CineTV ana ekranının üstündeki **telefon** düğmesi › telefonla QR kodu okutun › televizyonda **İzin ver**. Telefon ana ekranına eklerseniz uygulama gibi açılır.
- **YouTube'dan "TV'de izle"**: telefondaki YouTube uygulamasında yayınla (📺) simgesine basın ve **"<televizyonun adı> · YouTube"**'u seçin; video reklamsız TizenTube'da açılır.
- **Film sitesinden yayınlama (Web Video Cast)**: telefon sayfasında **Yayınla** › sayfanın adresini yapıştırın › **Aç**. Sayfa telefonda açılır; reklamları geçip filmin oynat tuşuna basın, film televizyonda başlar (gerçek iframe Referer ve Origin başlıkları otomatik yakalanıp CDN engelleri aşılır).
- **Anahtarlar** (isteğe bağlı, ücretsiz): CineTV › Ayarlar › **Yapay zekâ ve filmler**:
  - *TMDB*: Türkçe özetler ve afişler. themoviedb.org › hesap aç › Ayarlar › API › anahtar oluştur.
  - *Yapay zekâ*: "araba temalı filmler" gibi aramalar. En kolayı ücretsiz Gemini: aistudio.google.com › *Get API key*.
  - *MDBList*: IMDb / Rotten Tomatoes puanları. mdblist.com › Preferences › API.
- **Performans & Bellek**: Ayarlar › TV & Telefon › **Bellek ve Performans** (bir kez basın, anlık bellek temizliği yapar ve benchmark skorunu gösterir).
- **Tema**: Ayarlar › Tema: Alptug Default, Sıvı Cam, Metalik, Sinema Salonu, Film Dolabı, Neon Gece, OLED Siyah.

---

## Sorun çözme

| Sorun | Çözüm |
| :--- | :--- |
| "Yapı"ya 7 kez bastım, bir şey olmadı | Doğru satırda mısınız? Google TV'de *Android TV OS derlemesi*. Basarken her seferinde OK'a basın, kaydırmayın. |
| Geliştirici seçeneklerinde "Ağ hata ayıklama" yok | Sadece **USB hata ayıklama**'yı açın; çoğu televizyonda ağdan da çalışır. |
| Bilgisayar "televizyona bağlanılamadı" diyor | IP adresini yeniden kontrol edin; televizyon ve bilgisayar aynı Wi-Fi'de mi? Televizyonu kapatıp açın, yeniden deneyin. |
| `unauthorized` / izin sorusu çıkmadı | Televizyonun ekranına bakın: soru arkada kalmış olabilir. Çıkmadıysa Geliştirici seçenekleri › **USB hata ayıklama yetkilerini iptal et**, sonra yeniden. |
| Downloader "indirilemedi" diyor | Adresi harfi harfine yazın: `github.com/AlptugDag/cinetv-launcher/releases/latest/download/CineTV.apk` |
| "Uygulama yüklenmedi" | Eski bir CineTV farklı imzayla kurulu: Ayarlar › Uygulamalar › CineTV › Kaldır, sonra yeniden kurun. |
| **Android 9 veya 14'te Home / Geri tuşu eski ekrana atıyor veya Erişilebilirlik menüsü bulunamıyor** | Bazı TV üreticileri (Vestel, Philips, TCL vb.) kendi arayüzlerinde Erişilebilirlik veya Varsayılan Uygulama menüsünü gizler. Çözüm: CineTV Ayarlar › TV & Telefon menüsündeki **Ana Başlatıcı Yap** ve **Erişilebilirlik & Tuşlar** kutucuklarına basın; CineTV üretici arayüzünü atlayıp doğrudan sistemin orijinal çekirdek seçicisini tetikler. Ayrıca CineTV'deki dahili `BootReceiver` sayesinde TV fişten çekilip açılsa bile CineTV otomatik olarak uyanır. |
| **Sadece APK kurdum ama bazı bileşenler eksik kaldı** | CineTV Ayarlar › TV & Telefon › **Kurulum Sihirbazı**'nı açın. Eksik olan satıra dokunarak veya alttaki **"Tümünü Otomatik Tamamla"** butonuna basarak Nuvio, Nova Player, kumanda izinleri ve sanal RAM optimizasyonunu kumandanızla saniyeler içinde tamamlayabilirsiniz. |
| Telefondaki kumanda tuşları başka uygulamalarda çalışmıyor | Televizyonda çıkan *"hata ayıklamaya izin ver"* sorusuna **Her zaman** demediniz; CineTV'yi kapatıp açın, soru yeniden gelir veya Ayarlar › TV & Telefon › Erişilebilirlik & Tuşlar'ı kontrol edin. |
| YouTube'da televizyon listede yok | Telefon ve televizyon aynı Wi-Fi'de mi? YouTube'u kapatıp açın. Yine yoksa: TizenTube › Ayarlar › **TV koduyla bağla**, kodu telefonda YouTube › Ayarlar › **TV'de izle** › **TV kodunu gir**'e yazın. |
| Film özetleri İngilizce | Televizyonun dili Türkçe mi? (Ayarlar › Cihaz Tercihleri › Dil). TMDB anahtarı girerseniz özetler doğrudan Türkçe gelir. |
| Bir şey bozuldu, eskiye dönmek istiyorum | CineTV › Ayarlar › en altta kırmızı **CineTV'yi yok et!** Televizyonu hiçbir şey kurulmamış gibi adeta ilk günkü orijinal fabrika haline döndürür. |

Hâlâ çözülmediyse: bir yapay zekâya (2. Yol) *"CineTV'de şu sorun var: …, AGENTS.md'ye göre çöz"* deyin.

---

## %100 Geri Alınabilirlik: "CineTV'yi Yok Et!"

CineTV televizyonunuzda hiçbir zaman kalıcı, geri alınamaz bir hasar veya kilitli değişiklik bırakmaz. Dilediğiniz an CineTV › **Ayarlar** › ekranın en altındaki kırmızı **"CineTV'yi yok et!"** butonuna bastığınızda:

1. **Orijinal Başlatıcıya Dönüş**: Varsayılan başlatıcı kilidi kaldırılır; televizyonunuz kendi orijinal fabrika ana ekranına (Google TV / Android TV Home) anında geri döner.
2. **Google Asistan ve Ses Servisleri**: Sessize alınmış veya devre dışı bırakılmış tüm Google Asistan bileşenleri (`com.google.android.apps.googleassistant`, `KatnissAssistantService`, `VoiceActivity`) eksiksiz olarak yeniden açılır.
3. **Bellek & Kernel Standartları**: 2 GB sanal RAM genişletmesi, kernel takas alanı (`swappiness=60`) ve sistem animasyon ölçekleri orijinal fabrika değerlerine (1.0x) döndürülür.
4. **Görüntü, Ses ve DNS**: Orijinal görüntü kalibrasyonu, ses modları ve şifreli DNS ayarları sıfırlanıp fabrika varsayılanına döner.
5. **Erişilebilirlik & Ekran Koruyucu**: Kumanda tuş yakalama servisi ve saat ekran koruyucusu sistem servislerinden güvenle temizlenir.
6. **Temiz Kaldırma**: CineTV paketi sistemden tamamen silinir; televizyon adeta hiçbir şey yaşanmamış gibi eski haline tamamen geri döner.

---

## Güvenlik notu

Kurulum bittikten sonra Geliştirici seçenekleri › **Ağ hata ayıklama**'yı kapatabilirsiniz; ama o zaman
telefon kumandası yalnızca CineTV'nin içinde çalışır. Açık bırakırsanız sadece sizin izin verdiğiniz cihazlar
bağlanabilir.
