# Genera secrets comprimidos (gzip+base64) para GitHub (limite ~64KB).
# Desde la raiz del repo:
#   .\tools\bots\prepare_github_secrets.ps1

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$gs = Join-Path $root "app\google-services.json"
$st = Join-Path $root "tools\bots\state.json"

if (-not (Test-Path $gs)) {
    throw "Falta app/google-services.json"
}
if (-not (Test-Path $st)) {
    throw "Falta tools/bots/state.json. Ejecuta antes: node tools/bots/bot_farm.js ensure"
}

function To-GzipBase64([string]$path) {
    $bytes = [IO.File]::ReadAllBytes($path)
    $ms = New-Object IO.MemoryStream
    $gz = New-Object IO.Compression.GzipStream($ms, [IO.Compression.CompressionMode]::Compress)
    $gz.Write($bytes, 0, $bytes.Length)
    $gz.Close()
    return [Convert]::ToBase64String($ms.ToArray())
}

$gsB64 = To-GzipBase64 $gs
$stB64 = To-GzipBase64 $st

$outDir = Join-Path $PSScriptRoot "_secrets_out"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
Set-Content -Path (Join-Path $outDir "GOOGLE_SERVICES_JSON_B64.txt") -Value $gsB64 -NoNewline
Set-Content -Path (Join-Path $outDir "BOTS_STATE_B64.txt") -Value $stB64 -NoNewline

$gsLen = $gsB64.Length
$stLen = $stB64.Length
Write-Host ""
Write-Host "OK. Archivos en tools/bots/_secrets_out/ (gzip+base64)"
Write-Host "GOOGLE_SERVICES_JSON_B64 size: $gsLen bytes"
Write-Host "BOTS_STATE_B64 size: $stLen bytes"
if ($stLen -gt 60000) {
    Write-Host "AVISO: BOTS_STATE sigue grande; GitHub admite max ~64KB por secret."
}
Write-Host ""
Write-Host "Nombres exactos del secret (campo Name):"
Write-Host "  GOOGLE_SERVICES_JSON_B64"
Write-Host "  BOTS_STATE_B64"
Write-Host "En Secret pega SOLO el texto del .txt (una sola linea)."
Write-Host "Copia tambien los .txt al Escritorio si quieres."
