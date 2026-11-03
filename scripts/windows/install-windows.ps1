# CineTV installer for Windows: double-click CineTV-Kur.bat (it runs this file).
# Downloads Google's adb if it is missing, connects to the TV, installs the latest CineTV and sets it up.
# Nothing else is installed on the computer; everything lives in %LOCALAPPDATA%\CineTV.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Pkg = 'com.cinetv.launcher'
$Home_ = "$Pkg/.MainActivity"
$KeyService = "$Pkg/$Pkg.TvKeyInterceptorService"
$Dream = "$Pkg/$Pkg.ClockDream"
$ApkUrl = 'https://github.com/AlptugDag/cinetv-launcher/releases/latest/download/CineTV.apk'
$Work = Join-Path $env:LOCALAPPDATA 'CineTV'
New-Item -ItemType Directory -Force -Path $Work | Out-Null

function Say($t) { Write-Host "  $t" }
function Ok($t) { Write-Host "  [OK] $t" -ForegroundColor Green }
function Warn($t) { Write-Host "  [!] $t" -ForegroundColor Yellow }
function Stop-With($t) { Write-Host "  [X] $t" -ForegroundColor Red; Read-Host '  Kapatmak icin Enter'; exit 1 }

Write-Host ''
Write-Host '  CineTV kurulumu / installer' -ForegroundColor Cyan
Write-Host ''

# 1. adb
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) {
    $adb = Join-Path $Work 'platform-tools\adb.exe'
    if (-not (Test-Path $adb)) {
        Say 'adb indiriliyor (Google platform-tools)...'
        $zip = Join-Path $Work 'platform-tools.zip'
        Invoke-WebRequest 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip
        Expand-Archive $zip -DestinationPath $Work -Force
        Remove-Item $zip
    }
}
Ok 'adb hazir'

# 2. the TV
Say 'Televizyonda: Ayarlar > Cihaz Tercihleri > Hakkinda > "Yapi"ya 7 kez basin;'
Say 'sonra Gelistirici secenekleri > "Ag hata ayiklama" / "USB hata ayiklama" acin.'
Say 'Televizyonun IP adresi: Ayarlar > Ag ve Internet > baglandiginiz ag (ornek 192.168.1.50).'
$ip = (Read-Host '  Televizyonun IP adresi').Trim()
if (-not $ip) { Stop-With 'IP adresi girilmedi.' }
if ($ip -notmatch ':') { $ip = "${ip}:5555" }
& $adb disconnect $ip 2>$null | Out-Null
& $adb connect $ip | Out-Null
Say 'Televizyonda "USB hata ayiklamaya izin verilsin mi?" sorusu cikarsa "Her zaman izin ver"i isaretleyip Tamam deyin.'
$state = ''
for ($i = 0; $i -lt 40; $i++) {
    $state = (& $adb -s $ip get-state 2>$null)
    if ($state -eq 'device') { break }
    if ($i % 5 -eq 4) { & $adb connect $ip | Out-Null }
    Start-Sleep -Seconds 2
}
if ($state -ne 'device') { Stop-With "Televizyona baglanilamadi ($ip). IP adresini ve ag hata ayiklamanin acik oldugunu kontrol edin." }
Ok "Baglandi: $(& $adb -s $ip shell getprop ro.product.model)"

# 3. CineTV
$apk = Join-Path $Work 'CineTV.apk'
Say 'CineTV indiriliyor...'
Invoke-WebRequest $ApkUrl -OutFile $apk
$out = (& $adb -s $ip install -r $apk 2>&1) -join ' '
if ($out -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
    Warn 'Eski CineTV farkli bir imzayla kurulmus; yeniden kuruluyor.'
    & $adb -s $ip uninstall $Pkg | Out-Null
    $out = (& $adb -s $ip install $apk 2>&1) -join ' '
}
if ($out -notmatch 'Success') { Stop-With "Kurulamadi: $out" }
Ok 'CineTV kuruldu'

function Tv($cmd) { & $adb -s $ip shell $cmd 2>$null }
Tv "pm grant $Pkg android.permission.WRITE_SECURE_SETTINGS" | Out-Null
Tv "appops set $Pkg SYSTEM_ALERT_WINDOW allow" | Out-Null
Tv "pm grant $Pkg android.permission.BLUETOOTH_CONNECT" | Out-Null
Tv "cmd package set-home-activity $Home_" | Out-Null
$cur = (Tv 'settings get secure enabled_accessibility_services')
if ("$cur" -notmatch [regex]::Escape($KeyService)) {
    if (-not $cur -or "$cur" -eq 'null') { Tv "settings put secure enabled_accessibility_services $KeyService" | Out-Null }
    else { Tv "settings put secure enabled_accessibility_services '$($cur.Trim()):$KeyService'" | Out-Null }
}
Tv 'settings put secure accessibility_enabled 1' | Out-Null
Tv 'settings put secure screensaver_enabled 1' | Out-Null
Tv 'settings put secure screensaver_activate_on_sleep 1' | Out-Null
Tv "settings put secure screensaver_components $Dream" | Out-Null
Ok 'Ana ekran, kumanda tuslari ve ekran koruyucu ayarlandi'

# 4. optional keys (hidden while typed, handed to the TV once, then kept in its encrypted store)
function Secret($label) {
    $s = Read-Host "  $label (bos birakabilirsiniz)" -AsSecureString
    return [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))
}
$tmdb = Secret 'TMDB API anahtari (Turkce ozetler, afisler; themoviedb.org)'
if ($tmdb) { Tv "settings put secure tv_tmdb_api_key $tmdb" | Out-Null; Ok 'TMDB anahtari verildi' }
$ai = Secret 'Yapay zeka anahtari (Gemini / Claude / OpenAI / Grok)'
if ($ai) { Tv "settings put secure tv_ai_api_key $ai" | Out-Null; Ok 'Yapay zeka anahtari verildi' }

Tv "am start -n $Home_" | Out-Null
Write-Host ''
Ok 'Bitti! Kumandada Ana Sayfa tusuna basin: CineTV acilir.'
Say 'Ilk acilista televizyon bir kez "hata ayiklamaya izin ver" diye sorarsa "Her zaman" deyip izin verin.'
Say 'Telefon kumandasi: CineTV ana ekranindaki telefon dugmesi > QR kodu telefonla okutun.'
Write-Host ''
Read-Host '  Kapatmak icin Enter'
