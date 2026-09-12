# Descarga los modelos candidatos para el banco de pruebas (solo en el PC).
# Uso:  powershell -File tools/asr-bench/fetch_models.ps1 [-Big]
#   -Big: tambien los pesados (~1 GB), para responder "y si usamos el mejor?"
param([switch]$Big)
$ErrorActionPreference = "Stop"
$root = Join-Path $PSScriptRoot "models"
New-Item -ItemType Directory -Force $root | Out-Null

$packages = @(
    "sherpa-onnx-moonshine-tiny-en-int8",
    "sherpa-onnx-moonshine-base-en-int8",
    "sherpa-onnx-moonshine-base-en-quantized-2026-02-27",
    "sherpa-onnx-whisper-base.en",
    "sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8"
)
if ($Big) {
    $packages += @(
        "sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8",
        "sherpa-onnx-whisper-small.en"
    )
}

foreach ($pkg in $packages) {
    $dir = Join-Path $root $pkg
    if (Test-Path $dir) { Write-Output "ya esta: $pkg"; continue }
    $tar = Join-Path $root "$pkg.tar.bz2"
    if (-not (Test-Path $tar)) {
        Write-Output "descargando $pkg ..."
        $url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$pkg.tar.bz2"
        Invoke-WebRequest -Uri $url -OutFile $tar -UseBasicParsing
    }
    Write-Output "extrayendo $pkg ..."
    tar -xjf $tar -C $root
    Remove-Item $tar
}
Write-Output "listo"
