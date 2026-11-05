@echo off
rem Compila (si hace falta) y abre La Sacerdotisa mostrando los mensajes, para ver errores.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Sacerdotisa.ps1" -Consola
pause
