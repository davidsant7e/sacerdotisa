# Deja La Sacerdotisa arrancando con Windows (acceso directo en la carpeta Inicio) y la abre ahora.
# Para quitarla: Desinstalar.bat.

$ErrorActionPreference = "Stop"

$script = Join-Path $PSScriptRoot "Sacerdotisa.ps1"
$acceso = Join-Path ([Environment]::GetFolderPath("Startup")) "La Sacerdotisa.lnk"
$argumentos = "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$script`""

$shell = New-Object -ComObject WScript.Shell
$lnk = $shell.CreateShortcut($acceso)
$lnk.TargetPath = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"
$lnk.Arguments = $argumentos
$lnk.WorkingDirectory = $PSScriptRoot
$lnk.WindowStyle = 7
$lnk.Description = "La Sacerdotisa: corrector que explica (Ctrl+Alt+Win+E)"
$lnk.IconLocation = (Join-Path $PSScriptRoot "assets\sacerdotisa.ico")
$lnk.Save()

Write-Host "Acceso directo creado en: $acceso"
Write-Host "La Sacerdotisa arrancará sola cada vez que inicies sesión."

& $script -Consola
