<#
.SYNOPSIS
    Restaura un backup .dump de PostgreSQL y verifica la integridad de las tablas criticas (PRB-66).
.DESCRIPTION
    Permite restaurar el archivo generado por backup-casablanca.ps1 sobre la base de datos
    indicada (por defecto 'casablanca_restore_test' para simulacros seguros o 'casablanca' en produccion)
    y ejecuta un conteo de verificacion sobre mesa, usuario, producto, pedido y pago.
#>

param(
    [Parameter(Mandatory=$true)]
    [string]$DumpFile,
    [string]$TargetDb = "casablanca_restore_test",
    [string]$DbUser = "postgres",
    [string]$DbHost = "localhost",
    [int]$DbPort = 5432
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $DumpFile)) {
    throw "No se encontro el archivo de backup: $DumpFile"
}

$pgRestoreCmd = Get-Command "pg_restore" -ErrorAction SilentlyContinue
$pgRestoreExe = if ($pgRestoreCmd) { $pgRestoreCmd.Source } else { "C:\Program Files\PostgreSQL\16\bin\pg_restore.exe" }
$psqlCmd = Get-Command "psql" -ErrorAction SilentlyContinue
$psqlExe = if ($psqlCmd) { $psqlCmd.Source } else { "C:\Program Files\PostgreSQL\16\bin\psql.exe" }

Write-Host "=== [PRB-66] Iniciando Restore desde $DumpFile hacia BD '$TargetDb' ===" -ForegroundColor Cyan

# 1. Crear BD destino si no existe
$exists = & $psqlExe -h $DbHost -p $DbPort -U $DbUser -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$TargetDb';"
if ($exists -ne "1") {
    Write-Host "Creando base de datos destino '$TargetDb'..."
    & $psqlExe -h $DbHost -p $DbPort -U $DbUser -d postgres -c "CREATE DATABASE $TargetDb;" | Out-Null
}

# 2. Ejecutar pg_restore --clean --if-exists
& $pgRestoreExe -h $DbHost -p $DbPort -U $DbUser -d $TargetDb --clean --if-exists --no-owner $DumpFile

# 3. Verificar integridad de tablas clave
Write-Host "Verificando integridad de tablas restauradas en '$TargetDb'..." -ForegroundColor Green
$verifySql = @"
SELECT 'mesa' AS tabla, COUNT(*) AS registros FROM mesa
UNION ALL SELECT 'usuario', COUNT(*) FROM usuario
UNION ALL SELECT 'producto', COUNT(*) FROM producto
UNION ALL SELECT 'pedido', COUNT(*) FROM pedido
UNION ALL SELECT 'pago', COUNT(*) FROM pago;
"@

& $psqlExe -h $DbHost -p $DbPort -U $DbUser -d $TargetDb -c $verifySql
Write-Host "=== Restore y verificacion completados ===" -ForegroundColor Cyan
