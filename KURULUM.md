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
| Claude, Antigravity, Gemini gibi bir AI ajanı (veya Terminal) | [1. Yol: Yapay Zekâ / AI Agent ile Kurulum (Tavsiye Edilen)](#1-yol-yapay-zekâ--ai-agent-ile-kurulum-tavsiye-edilen--100-tam-otomatik) | 2 dk | **Sıfır çaba, tüm eklenti ve programlar %100 otomatik kurulur** |
| Sadece televizyon ve kumandası | [2. Yol: Doğrudan Televizyona APK ile](#2-yol-doğrudan-televizyona-apk-ile-kurulum) | 3 dk | **Arka planda sessiz otomatik yapılandırma** |

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

## 1. Yol: Yapay Zekâ / AI Agent ile Kurulum (Tavsiye Edilen — %100 Tam Otomatik)

Bilgisayarınızda dosya okuyup komut çalıştırabilen bir yapay zekâ ajanı olmalı: **Antigravity** (Google DeepMind, tamamen ücretsiz), **Claude Desktop** (Claude Code), **Cursor**, **Gemini CLI**, **Codex** gibi.

1. **Adım 1**'i yapın ve televizyonun IP adresini not edin (Ayarlar › Ağ ve İnternet › bağlı olduğunuz Wi-Fi › IP adresi).
2. Yapay zekâya aynen şunu yazın:
   > https://github.com/AlptugDag/cinetv-launcher adresindeki AGENTS.md dosyasını oku ve CineTV'yi televizyonuma kur. Televizyonumun IP adresi: <TV_IP_ADRESİNİZ>.
3. Yapay zekâ televizyonunuza bağlanır, onay isterse **Her zaman izin ver** › **Tamam**'a basın.
4. Yapay zekâ; CineTV ana ekranını, Nuvio medya merkezini, Nova Player'ı, AirPlay'i, reklamsız YouTube'u (TizenTube), 2 GB sanal RAM (Z-RAM) artışını, debloat sadeleştirmesini, hazır profil dosyalarını (Xperience, AIOMetadata) ve tüm seçkin eklentileri (TR Altyazı, Anthology, Netflix, Xperience) **tek adımda, sıfır çaba ile %100 tam otomatik** kurar ve televizyonu sinema modunda açar.

---

### Geliştiriciler & Terminal Meraklıları İçin: Tek Komutla Otomatik Kurulum

```bash
git clone https://github.com/AlptugDag/cinetv-launcher
cd cinetv-launcher
./install.sh --tv <TV_IP_ADRESİNİZ>
```

`adb` yoksa: Mac `brew install android-platform-tools`, Ubuntu `sudo apt install adb`. Anahtarlar isteğe bağlı:
`CINETV_TMDB_KEY=... CINETV_AI_KEY=... ./install.sh --tv <TV_IP_ADRESİNİZ>`. Ayrıntılar: [AGENTS.md](AGENTS.md).

---

## 2. Yol: Doğrudan Televizyona APK ile Kurulum

Bilgisayar veya terminal kullanmadan doğrudan televizyonunuzla kurmak isterseniz:

1. Televizyonda **Play Store**'u açın, **Downloader** diye aratın (turuncu simgeli, *AFTVnews* yapımı) ve **Yükle**'ye basın. (Ya da `CineTV.apk`'yı bilgisayardan bir USB belleğe atıp televizyonunuza takabilirsiniz.)
2. Downloader'ı açın. Depolama izni isterse **İzin ver** deyin.
3. Bilinmeyen kaynaklara izin verin:
   - Android TV: Ayarlar › Cihaz Tercihleri › Güvenlik ve kısıtlamalar › **Bilinmeyen kaynaklar** › Downloader: **Açık**
   - Google TV: Ayarlar › Uygulamalar › Güvenlik ve kısıtlamalar › **Bilinmeyen kaynaklar** › Downloader: **Açık**
4. Downloader'ın ana ekranındaki adres kutusuna kumandayla şunu yazın, sonra **Go**:
   ```
   github.com/AlptugDag/cinetv-launcher/releases/latest/download/CineTV.apk
   ```
   (Büyük-küçük harfe dikkat: `AlptugDag` ve `CineTV.apk`.)
5. İndirme bitince **Yükle** (Install), sonra **Aç** (Open).
6. Televizyon *"USB hata ayıklamaya izin verilsin mi?"* diye sorarsa: **"Bu bilgisayardan her zaman izin ver"** kutusunu işaretleyin ve **Tamam**'a basın.
7. **Tam Otomatik Başlatma**: CineTV ilk açıldığında arka planda Nuvio medya merkezini, hazır Türkçe film/dizi kaynaklarını, altyazı eklentilerini (Xperience, Netflix Kataloğu, Anthology, TR-Altyazı), video oynatıcıyı ve 2 GB sanal RAM optimizasyonunu **hiçbir kullanıcı müdahalesi gerektirmeden sessizce tamamlar** ve sinematik karşılama ekranıyla ana ekranınızı açar.

> Bileşen durumunu kontrol etmek veya elle incelemek isterseniz dilediğiniz zaman CineTV › Ayarlar › TV & Telefon › **Kurulum Sihirbazı & Bileşen Kontrolü** üzerinden tüm bileşenleri görebilirsiniz.

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
