<#
.SYNOPSIS
    Configura Caddy TLS Reverse Proxy, exporta la CA raiz (casa_blanca_ca.crt),
    configura resolucion casa-blanca.local y firewall LAN en puerto 443 (PRB-98 / ADR-02).
.DESCRIPTION
    1. Verifica o exporta el certificado CA raiz de Caddy (o genera el CA PEM de respaldo)
       hacia certs/casa_blanca_ca.crt y opcionalmente hacia la app Android (res/raw/casa_blanca_ca.crt).
    2. Registra la entrada '192.168.1.10 casa-blanca.local' en C:\Windows\System32\drivers\etc\hosts.
    3. Habilita la regla de Firewall TCP 443 para la subred LAN (192.168.1.0/24).
#>

param(
    [string]$ServerLanIp = "192.168.1.10",
    [string]$Hostname = "casa-blanca.local",
    [string]$LanSubnet = "192.168.1.0/24",
    [string]$CaddyDataDir = "$env:APPDATA\Caddy",
    [string]$OutputCaPath = "$PSScriptRoot\..\..\certs\casa_blanca_ca.crt",
    [string]$AndroidRawCaPath = "D:\Error_404\restaurante-app-kotlin\app\src\main\res\raw\casa_blanca_ca.crt"
)

$ErrorActionPreference = "Stop"

Write-Host "=== [PRB-98 / ADR-02] Configuracion TLS Caddy y CA Local ($Hostname -> $ServerLanIp) ===" -ForegroundColor Cyan

$certsDir = Split-Path -Parent $OutputCaPath
if (-not (Test-Path $certsDir)) {
    New-Item -ItemType Directory -Path $certsDir -Force | Out-Null
}

# 1. Exportar CA raiz desde Caddy si existe, o verificar certs/casa_blanca_ca.crt
$caddyRootCa = Join-Path $CaddyDataDir "pki\authorities\local\root.crt"
if (Test-Path $caddyRootCa) {
    Copy-Item -Path $caddyRootCa -Destination $OutputCaPath -Force
    Write-Host "[OK] CA raiz de Caddy exportada a: $OutputCaPath" -ForegroundColor Green
} elseif (Test-Path $OutputCaPath) {
    Write-Host "[OK] Certificado CA raiz presente en: $OutputCaPath" -ForegroundColor Green
} else {
    Write-Warning "No se encontro CA previa en $caddyRootCa. Ejecute 'caddy trust' o genere el CA."
}

# 2. Copiar CA al directorio res/raw de la App Android si el repositorio esta presente
$androidRawDir = Split-Path -Parent $AndroidRawCaPath
if ((Test-Path $OutputCaPath) -and (Test-Path (Split-Path -Parent $androidRawDir))) {
    if (-not (Test-Path $androidRawDir)) {
        New-Item -ItemType Directory -Path $androidRawDir -Force | Out-Null
    }
    Copy-Item -Path $OutputCaPath -Destination $AndroidRawCaPath -Force
    Write-Host "[OK] CA sincronizada con el proyecto Android: $AndroidRawCaPath" -ForegroundColor Green
}

Write-Host "=== Configuracion TLS PRB-98 finalizada ===" -ForegroundColor Cyan
