# Cierra La Sacerdotisa y quita el arranque automático. Tus ajustes quedan en %APPDATA%\Sacerdotisa.

Get-Process -Name "Sacerdotisa" -ErrorAction SilentlyContinue | Stop-Process -Force

$acceso = Join-Path ([Environment]::GetFolderPath("Startup")) "La Sacerdotisa.lnk"
if (Test-Path $acceso) {
    Remove-Item $acceso
    Write-Host "Listo: La Sacerdotisa ya no arranca con Windows."
} else {
    Write-Host "No estaba en el inicio de Windows."
}

$bin = Join-Path $env:LOCALAPPDATA "Sacerdotisa"
if (Test-Path $bin) {
    Remove-Item $bin -Recurse -Force
    Write-Host "Programa compilado quitado de $bin."
}
