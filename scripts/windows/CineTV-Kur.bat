@echo off
rem CineTV: double-click to install on a TV (Windows 10/11). Runs install-windows.ps1 next to this file,
rem or downloads it when this file was downloaded on its own.
cd /d "%~dp0"
if not exist "install-windows.ps1" powershell -NoProfile -Command "[Net.ServicePointManager]::SecurityProtocol='Tls12'; Invoke-WebRequest 'https://raw.githubusercontent.com/AlptugDag/cinetv-launcher/main/scripts/windows/install-windows.ps1' -OutFile 'install-windows.ps1'"
powershell -NoProfile -ExecutionPolicy Bypass -File "install-windows.ps1"
