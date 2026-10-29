# CineTV for Windows PC 🎬

CineTV Launcher'ın fiziksel 3D DVD vitrini, Nuvio, TMDB ve Gamze AI asistanı entegrasyonuyla Windows PC masaüstü sürümü.

---

## 🚀 Çalıştırma Yöntemleri

### 1. Anında Çalıştırma (Kurulumsuz / Sıfır Bağımlılık)
Windows 10 ve 11 işletim sistemlerinde yerleşik olarak bulunan Microsoft Edge WebView motorunu kullanarak anında tam ekran sinematik modda açılır:
1. `launch.bat` dosyasına çift tıklayın.
2. CineTV tam ekran, donanım hızlandırmalı ve 60/120 FPS akıcılıkta doğrudan açılacaktır.

### 2. Standalone Electron Masaüstü Uygulaması
Node.js ortamında bağımsız masaüstü penceresi olarak çalıştırmak veya `.exe` kurulum paketi üretmek için:
```bash
cd windows
npm install
npm start
```

Masaüstü Windows yükleyicisi (`CineTV-Setup.exe`) oluşturmak için:
```bash
npm run dist
```

---

## 🌟 Dahil Edilen Özellikler ve Entegrasyonlar

- **Fiziksel 3D DVD Vitrini:** 
  - Gerçekçi plastik DVD kılıfı parlama (sheen), sırt ve 4K UHD kabartması.
  - Klavye (Sağ/Sol Ok, Enter, Esc, F), fare veya kumanda ile pürüzsüz vitrin gezinmesi.
  - DVD kutusuna tıklandığında açılan 3D kapak:
    - **Sol Sayfa:** Arşivsel parşömen kâğıdı, filmin karesinden oluşturulmuş yarı saydam filigran dokusu (watermark), kâğıt lifleri ve keçe uçlu kalemle yazılmış film künyesi/özeti.
    - **Sağ Sayfa:** Gökkuşağı kırınım ışıklı holografik optik disk, gecikmesiz YouTube UI'sız fragman yuvası ve **Nuvio'da Başlat** düğmesi.

- **Nuvio & Eklenti Entegrasyonu:**
  - `nuvio://` ve `stremio://` derin bağlantıları ile doğrudan Windows Nuvio uygulamasına bağlanır.
  - Alptuğ'un Özel Koleksiyonları (`alptugun-filmleri-template.json`) ve Xperience eklenti profilleriyle uyumlu katalog.

- **Gamze Sinema Asistanı:**
  - Google Gemini API altyapısıyla Türkçe sinema sohbeti, tür/ruh hali önerileri ve sesli arama.

- **Sinematik Oda Temaları:**
  - **Film Dolabı (Closet):** Perspektifli yan raflar, tavan ışık tüpü ve derinlik hissi veren yürüyüş odası.
  - **Sinema Salonu (Cinema Hall):** Ekranı çevreleyen hareketli tiyatro kadife perdeleri.
  - **CineTV Klasik (Glass):** Apple VisionOS sıvı cam HUD kask vizörü.
