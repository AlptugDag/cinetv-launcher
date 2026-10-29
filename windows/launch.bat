@echo off
setlocal
title CineTV Launcher - Windows PC

echo ========================================================
echo   CineTV by Alptug Dag - Windows PC Native Launcher
echo   Sinematik 3D Vitrin, Nuvio ve Gamze Asistan
echo ========================================================
echo.

cd /d "%~dp0"

:: Check if Microsoft Edge is available (Windows 10/11 default)
set "EDGE_EXE=%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe"
if not exist "%EDGE_EXE%" set "EDGE_EXE=%ProgramFiles%\Microsoft\Edge\Application\msedge.exe"

if exist "%EDGE_EXE%" (
    echo Starting CineTV in Native Desktop App Mode...
    start "" "%EDGE_EXE%" --app="file:///%~dp0index.html" --start-fullscreen --disable-gpu-vsync --enable-smooth-scrolling --force-dark-mode
    exit /b 0
)

:: Fallback to Chrome if Edge is absent
set "CHROME_EXE=%ProgramFiles%\Google\Chrome\Application\chrome.exe"
if not exist "%CHROME_EXE%" set "CHROME_EXE=%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe"

if exist "%CHROME_EXE%" (
    echo Starting CineTV via Chrome App Mode...
    start "" "%CHROME_EXE%" --app="file:///%~dp0index.html" --start-fullscreen
    exit /b 0
)

:: Universal fallback: default system browser
start "" "file:///%~dp0index.html"
exit /b 0
