# La Sacerdotisa para Windows: selecciona un texto en cualquier programa, pulsa Ctrl+Alt+Win+E
# y te explica cada error (mayúsculas, tildes, palabras que se confunden…).
#
# Este script compila el programa la primera vez (o cuando cambia el código) con el compilador
# de C# que ya trae Windows, y lo abre. No hay que instalar nada.
#
#   -Consola   muestra los mensajes de compilación (lo usa Probar.bat)

param([switch]$Consola)

$ErrorActionPreference = "Stop"

$fuentes = @("Explicador.cs", "Ventana.cs", "App.cs", "Program.cs") | ForEach-Object { Join-Path $PSScriptRoot "src\$_" }
$icono = Join-Path $PSScriptRoot "assets\sacerdotisa.ico"
$datos = Join-Path $PSScriptRoot "..\extension\datos\explicaciones.json"
$bin = Join-Path $env:LOCALAPPDATA "Sacerdotisa"
$exe = Join-Path $bin "Sacerdotisa.exe"

function Escribir($texto) { if ($Consola) { Write-Host $texto } }

$compilar = -not (Test-Path $exe)
if (-not $compilar) {
    $fechaExe = (Get-Item $exe).LastWriteTime
    $compilar = @(($fuentes + $icono + $datos) | Where-Object { (Get-Item $_).LastWriteTime -gt $fechaExe }).Count -gt 0
}

if ($compilar) {
    $csc = Join-Path $env:WINDIR "Microsoft.NET\Framework64\v4.0.30319\csc.exe"
    if (-not (Test-Path $csc)) { $csc = Join-Path $env:WINDIR "Microsoft.NET\Framework\v4.0.30319\csc.exe" }
    if (-not (Test-Path $csc)) { throw "No se encontró el compilador de C# de .NET Framework 4 ($csc)." }

    Get-Process -Name "Sacerdotisa" -ErrorAction SilentlyContinue | Stop-Process -Force
    Start-Sleep -Milliseconds 500

    New-Item -ItemType Directory -Force -Path $bin | Out-Null
    Copy-Item $icono (Join-Path $bin "sacerdotisa.ico") -Force
    Copy-Item $datos (Join-Path $bin "explicaciones.json") -Force
    Escribir "Compilando La Sacerdotisa..."
    $salida = & $csc /nologo /target:winexe /optimize+ /codepage:65001 "/out:$exe" "/win32icon:$icono" `
        /r:System.dll /r:System.Core.dll /r:System.Drawing.dll /r:System.Windows.Forms.dll /r:System.Runtime.Serialization.dll `
        $fuentes 2>&1
    if ($LASTEXITCODE -ne 0) {
        $texto = ($salida | Out-String)
        if ($Consola) { Write-Host $texto -ForegroundColor Red }
        else {
            Add-Type -AssemblyName System.Windows.Forms
            [System.Windows.Forms.MessageBox]::Show("No se pudo compilar La Sacerdotisa:`n`n$texto", "La Sacerdotisa") | Out-Null
        }
        exit 1
    }
    Escribir "Listo: $exe"
}

if (Get-Process -Name "Sacerdotisa" -ErrorAction SilentlyContinue) {
    Escribir "La Sacerdotisa ya está abierta (ícono junto al reloj)."
    exit 0
}

Start-Process -FilePath $exe
Escribir "La Sacerdotisa abierta: selecciona un texto en cualquier programa y pulsa Ctrl+Alt+Win+E."
