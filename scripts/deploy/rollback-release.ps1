<#
.SYNOPSIS
    Procedimiento automatizado de Rollback de version y base de datos (PRB-65).
.DESCRIPTION
    Detiene el servicio CasaBlancaBackendJava, restaura el artefacto .jar previo desde
    C:\CasaBlanca\releases, opcionalmente restaura el ultimo dump de PostgreSQL, reinicia
    el servicio y verifica /api/v1/health.
#>

param(
    [Parameter(Mandatory=$true)]
    [string]$PreviousJarPath,
    [string]$TargetJarPath = "C:\CasaBlanca\backend\restaurante-backend.jar",
    [string]$DbDumpPath = "",
    [string]$ServiceName = "CasaBlancaBackendJava",
    [string]$HealthUrl = "http://localhost:8080/api/v1/health"
)

$ErrorActionPreference = "Stop"

Write-Host "=== [PRB-65] Iniciando Rollback de Produccion - Restaurante Casa Blanca ===" -ForegroundColor Yellow

if (-not (Test-Path $PreviousJarPath)) {
    throw "No existe el artefacto previo en: $PreviousJarPath"
}

# 1. Detener servicio Backend
if (Get-Service -Name $ServiceName -ErrorAction SilentlyContinue) {
    Write-Host "[1/4] Deteniendo servicio $ServiceName..."
    Stop-Service -Name $ServiceName -Force
}

# 2. Restaurar artefacto .jar anterior
$backupFailedJar = "$TargetJarPath.rollback-$(Get-Date -Format 'yyyyMMddHHmmss').bak"
if (Test-Path $TargetJarPath) {
    Copy-Item -Path $TargetJarPath -Destination $backupFailedJar -Force
}
Copy-Item -Path $PreviousJarPath -Destination $TargetJarPath -Force
Write-Host "[2/4] Artefacto .jar restaurado desde $PreviousJarPath" -ForegroundColor Green

# 3. Restaurar base de datos si se especifico un dump
if ($DbDumpPath -ne "" -and (Test-Path $DbDumpPath)) {
    Write-Host "[3/4] Ejecutando restore de base de datos desde $DbDumpPath..."
    & "$PSScriptRoot\..\backup\restore-casablanca.ps1" -DumpFile $DbDumpPath
} else {
    Write-Host "[3/4] Sin rollback de base de datos solicitado (solo binario)."
}

# 4. Iniciar servicio y verificar Healthcheck
if (Get-Service -Name $ServiceName -ErrorAction SilentlyContinue) {
    Write-Host "[4/4] Iniciando servicio $ServiceName..."
    Start-Service -Name $ServiceName
    Start-Sleep -Seconds 5
}

try {
    $resp = Invoke-RestMethod -Uri $HealthUrl -Method Get -TimeoutSec 5
    Write-Host "[OK] Rollback verificado en $HealthUrl -> status=$($resp.status), db=$($resp.db)" -ForegroundColor Green
} catch {
    Write-Warning "El servicio aun esta inicializando o requiere verificacion manual en $HealthUrl"
}
