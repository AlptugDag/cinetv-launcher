# CineTV kurulum rehberi

Bu rehber hiç bilgisayar bilmeyen biri için yazıldı. Her adımı sırayla yapın; bir adımda takılırsanız en
alttaki **Sorun çözme** tablosuna bakın. *(English: [the short version is in the README](README.md#install-in-one-command).)*

**İndirme bağlantısı (her zaman en son sürüm):**
`https://github.com/AlptugDag/cinetv-launcher/releases/latest/download/CineTV.apk`

---

## Hangi yolu seçmeliyim?

| Elinizde ne var? | Yol | Süre | Kişiye özel kurulum (sorular, Nuvio rafları)? |
| :--- | :--- | :--- | :--- |
| Sadece televizyon ve kumandası | [Yol A: Sadece TV](#yol-a-sadece-televizyonla-bilgisayar-gerekmez) | 10 dk | Hayır (CineTV'nin kendisi tam çalışır) |
| Windows bilgisayar | [Yol B: Windows'ta çift tıkla](#yol-b-windowsta-çift-tıkla) | 5 dk | Hayır |
| Mac bilgisayar | [Yol C: Mac'te çift tıkla](#yol-c-macte-çift-tıkla) | 10 dk | **Evet** |
| Claude Desktop, Antigravity, Gemini, Cursor gibi bir yapay zekâ | [Yol D: Yapay zekâya kurdur](#yol-d-bir-yapay-zekâya-kurdurun-en-iyi-sonuç) | 15 dk | **Evet, en iyisi** |
| Terminal kullanmayı biliyorum | [Yol E: Terminal](#yol-e-terminal-mac--linux--windows-wsl) | 5 dk | **Evet** |

Hepsinde önce **Adım 1**'i yapın.

---

## Adım 1: Televizyonda "Geliştirici seçenekleri"ni açın (hepsi için gerekli)

CineTV, kumanda tuşlarını her uygulamada çalıştırmak ve kendini ana ekran yapmak için televizyonun
"hata ayıklama" özelliğini kullanır. Bu, garantiyi bozmaz ve istediğiniz zaman kapatılabilir.

**1.1. Geliştirici olun.** Menü adları markaya göre biraz değişir; size uyan satırı izleyin:

| Televizyon | Gideceğiniz yer | Ne yapacaksınız |
| :--- | :--- | :--- |
| **Android TV** (Arçelik, Beko, Grundig eski, Sony, Philips, TCL eski…) | Ayarlar › Cihaz Tercihleri › Hakkında › **Yapı** | **Yapı** satırının üstüne gelip kumandada **OK**'a **7 kez** basın |
| **Google TV** (Grundig yeni, TCL yeni, Chromecast, Hisense…) | Ayarlar › Sistem › Hakkında › **Android TV OS derlemesi** | Aynı satıra **7 kez OK** |

Ekranda *"Artık bir geliştiricisiniz"* yazısı çıkar. (Şifre sorarsa televizyonun kilit PIN'ini girin.)

**1.2. Hata ayıklamayı açın.** Bir önceki menüye dönün; artık **Geliştirici seçenekleri** var:

- Android TV: Ayarlar › Cihaz Tercihleri › **Geliştirici seçenekleri**
- Google TV: Ayarlar › Sistem › **Geliştirici seçenekleri**

İçinde şunları **Açık** yapın (hangisi varsa):
- **USB hata ayıklama**
- **Ağ hata ayıklama** (ya da *ADB over network* / *Kablosuz hata ayıklama*)

**1.3. Televizyonun IP adresini not edin** (Yol B, C, D, E için):
Ayarlar › Ağ ve İnternet › bağlı olduğunuz Wi-Fi'nin ya da kablonun adı › **IP adresi**.
`192.168.1.50` gibi bir şeydir.

> Telefonunuz ve bilgisayarınız televizyonla **aynı Wi-Fi'ye** bağlı olmalı.

---

## Yol A: Sadece televizyonla (bilgisayar gerekmez)

1. Televizyonda **Play Store**'u açın, **Downloader** diye aratın (turuncu simgeli, *AFTVnews* yapımı) ve
   **Yükle**'ye basın.
2. Downloader'ı açın. İzin isterse **İzin ver** deyin.
3. Bilinmeyen kaynaklara izin verin (Downloader ilk kurulumda kendisi sorar; sormazsa):
   - Android TV: Ayarlar › Cihaz Tercihleri › Güvenlik ve kısıtlamalar › **Bilinmeyen kaynaklar** › Downloader: **Açık**
   - Google TV: Ayarlar › Uygulamalar › Güvenlik ve kısıtlamalar › **Bilinmeyen kaynaklar** › Downloader: **Açık**
4. Downloader'ın ana ekranındaki adres kutusuna kumandayla şunu yazın, sonra **Go**:
   ```
   github.com/AlptugDag/cinetv-launcher/releases/latest/download/CineTV.apk
   ```
   (Büyük-küçük harfe dikkat: `AlptugDag` ve `CineTV.apk`.)
5. İndirme bitince **Yükle** (Install), sonra **Aç** (Open).
6. Televizyon *"USB hata ayıklamaya izin verilsin mi?"* diye sorar: **"Bu bilgisayardan her zaman izin ver"**
   kutusunu işaretleyin ve **Tamam**'a basın. (Çıkmazsa Adım 1.2'yi kontrol edin.)
   CineTV bununla kendini ana ekran yapar, kumanda tuşlarını ve ekran koruyucuyu ayarlar.
7. Kumandada **Ana Sayfa** (ev) tuşuna basın. *"Hangi ana ekran?"* diye sorarsa **CineTV** › **Her zaman**.
8. Downloader'a dönüp indirdiği dosyayı silebilirsiniz (*Files* › CineTV.apk › Sil).

Bitti. Film özetleri, afişler ve yapay zekâ araması için [anahtarları girin](#kurulumdan-sonra) (isteğe bağlı).

---

## Yol B: Windows'ta çift tıkla

1. Bu dosyayı indirin: **[CineTV-Kur.bat](https://github.com/AlptugDag/cinetv-launcher/releases/latest/download/CineTV-Kur.bat)**
2. İndirilenler klasöründe **CineTV-Kur.bat**'a çift tıklayın.
   - *"Windows bilgisayarınızı korudu"* uyarısı çıkarsa: **Ek bilgi** › **Yine de çalıştır**.
3. Siyah pencere televizyonun **IP adresini** sorar (Adım 1.3). Yazın, **Enter**.
4. Televizyonda *"USB hata ayıklamaya izin verilsin mi?"* çıkar: **Her zaman izin ver**'i işaretleyip **Tamam**.
5. Pencere CineTV'yi indirip kurar, ayarlar. İsterseniz TMDB ve yapay zekâ anahtarını sorar (boş bırakıp
   Enter'a basabilirsiniz).
6. *"Bitti!"* yazınca televizyonda **Ana Sayfa** tuşuna basın.

Bilgisayara kalıcı bir şey kurulmaz; gereken her şey `%LOCALAPPDATA%\CineTV` klasöründedir.

---

## Yol C: Mac'te çift tıkla

1. Bu dosyayı indirin: **[CineTV-Kur.command](https://github.com/AlptugDag/cinetv-launcher/releases/latest/download/CineTV-Kur.command)**
2. İlk sefer için: **Terminal** uygulamasını açın (Spotlight'ta "Terminal" yazın), şunu yapıştırıp Enter'a basın:
   ```bash
   chmod +x ~/Downloads/CineTV-Kur.command && xattr -d com.apple.quarantine ~/Downloads/CineTV-Kur.command 2>/dev/null; open ~/Downloads/CineTV-Kur.command
   ```
   (Sonraki seferlerde dosyaya çift tıklamanız yeter.)
3. Kurulum televizyonu bulur ya da IP adresini sorar, sonra **sizi tanımak için sorular sorar**: dil, ne
   izlersiniz, ne izlemezsiniz, hangi takımı tutarsınız, hangi servislere abonesiniz… Her sorunun hazır bir
   cevabı vardır; bilmiyorsanız Enter'a basın.
4. Televizyonda *"USB hata ayıklamaya izin verilsin mi?"*: **Her zaman izin ver** › **Tamam**.
5. Sonunda yazan **Nuvio'da bitir** adımlarını uygulayın (rafları içe aktarmak gibi; 2 dakika).

*"python3 bulunamadı"* derse: Mac bir pencere açıp "geliştirici araçlarını yükle" der; **Yükle**'ye basın, bitince
dosyayı yeniden açın.

---

## Yol D: Bir yapay zekâya kurdurun (en iyi sonuç)

Bilgisayarınızda dosya okuyup komut çalıştırabilen bir yapay zekâ olmalı: **Claude Desktop** (Claude Code),
**Antigravity** (ücretsiz), **Gemini CLI**, **Cursor**, **Codex** gibi. Tarayıcıdaki sohbet pencereleri
(sadece yazışan) televizyona bağlanamaz.

1. Adım 1'i yapın, IP adresini not edin.
2. Yapay zekâya aynen şunu yazın:
   > https://github.com/AlptugDag/cinetv-launcher adresindeki projeyi indir, AGENTS.md dosyasını baştan sona
   > oku ve oradaki adımlarla CineTV'yi televizyonuma kur. Televizyonumun IP adresi 192.168.1.50.
   > Bana soruları Türkçe sor.
3. Yapay zekâ size soru sorar (ne izlediğiniz, dil, servisler…), cevaplayın. Televizyonda izin sorusu çıkınca
   **Her zaman izin ver** › **Tamam**.
4. Komut çalıştırmak için onay isterse **İzin ver** deyin.

Yapay zekâ Nuvio'daki son adımları da (raflar, eklentiler, altyazı) televizyonun ekranına bakarak kendisi yapar.

---

## Yol E: Terminal (Mac / Linux / Windows WSL)

```bash
git clone https://github.com/AlptugDag/cinetv-launcher
cd cinetv-launcher
./install.sh --tv 192.168.1.50
```

`adb` yoksa: Mac `brew install android-platform-tools`, Ubuntu `sudo apt install adb`. Anahtarlar isteğe bağlı:
`CINETV_TMDB_KEY=... CINETV_AI_KEY=... ./install.sh --tv 192.168.1.50`. Ayrıntılar: [AGENTS.md](AGENTS.md).

---

## Kurulumdan sonra

- **Telefon kumandası**: CineTV ana ekranının üstündeki **telefon** düğmesi › telefonla QR kodu okutun ›
  televizyonda **İzin ver**. Telefon ana ekranına eklerseniz uygulama gibi açılır.
- **YouTube'dan "TV'de izle"**: telefondaki YouTube uygulamasında yayınla (📺) simgesine basın ve
  **"<televizyonun adı> · YouTube"**'u seçin; video reklamsız TizenTube'da açılır.
- **Film sitesinden yayınlama**: telefon sayfasında **Yayınla** › sayfanın bağlantısını yapıştırın › **TV'de oynat**.
- **Anahtarlar** (isteğe bağlı, ücretsiz): CineTV › Ayarlar › **Yapay zekâ ve filmler**:
  - *TMDB*: Türkçe özetler ve afişler. themoviedb.org › hesap aç › Ayarlar › API › anahtar oluştur.
  - *Yapay zekâ*: "araba temalı filmler" gibi aramalar. En kolayı ücretsiz Gemini: aistudio.google.com › *Get API key*.
  - *MDBList*: IMDb / Rotten Tomatoes puanları. mdblist.com › Preferences › API.
- **Performans**: Ayarlar › Performans › **Otomatik Belirle** (bir kez basın, televizyona en uygun modu seçer).
- **Tema**: Ayarlar › Tema: Alptug Default, Sıvı Cam, Metalik, Sinema Salonu, DVD Dükkânı, Neon Gece, OLED Siyah.

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
| Ana Sayfa tuşu eski ekranı açıyor | CineTV'yi bir kez açın (izin sorusuna **Her zaman**); kendini ana ekran yapar. Hâlâ olmuyorsa Yol B, C ya da E'den biriyle kurun. |
| Telefondaki kumanda tuşları başka uygulamalarda çalışmıyor | Televizyonda çıkan *"hata ayıklamaya izin ver"* sorusuna **Her zaman** demediniz; CineTV'yi kapatıp açın, soru yeniden gelir. |
| YouTube'da televizyon listede yok | Telefon ve televizyon aynı Wi-Fi'de mi? YouTube'u kapatıp açın. Yine yoksa: TizenTube › Ayarlar › **TV koduyla bağla**, kodu telefonda YouTube › Ayarlar › **TV'de izle** › **TV kodunu gir**'e yazın. |
| Film özetleri İngilizce | Televizyonun dili Türkçe mi? (Ayarlar › Cihaz Tercihleri › Dil). TMDB anahtarı girerseniz özetler doğrudan Türkçe gelir. |
| Bir şey bozuldu, eskiye dönmek istiyorum | CineTV › Ayarlar › en altta kırmızı **CineTV'yi yok et!** Her şeyi eski hâline getirir. |

Hâlâ çözülmediyse: bir yapay zekâya (Yol D) *"CineTV'de şu sorun var: …, AGENTS.md'ye göre çöz"* deyin.

---

## Güvenlik notu

Kurulum bittikten sonra Geliştirici seçenekleri › **Ağ hata ayıklama**'yı kapatabilirsiniz; ama o zaman
telefon kumandası yalnızca CineTV'nin içinde çalışır. Açık bırakırsanız sadece sizin izin verdiğiniz cihazlar
bağlanabilir.
