<#
.SYNOPSIS
    Script de Backup Automatico Diario de PostgreSQL y Media del Catalogo (PRB-66).
.DESCRIPTION
    1. Ejecuta pg_dump en formato custom comprimido (-Fc) de la BD 'casablanca'.
    2. Comprime la carpeta de imagenes del catalogo (media).
    3. Aplica politica de retencion de 30 dias (elimina backups antiguos).
    4. Registra bitacora detallada con timestamp, tamano y hash SHA256.
#>

param(
    [string]$DbName = "casablanca",
    [string]$DbUser = "postgres",
    [string]$DbHost = "localhost",
    [int]$DbPort = 5432,
    [string]$BackupRoot = "C:\CasaBlanca\backups",
    [string]$MediaSourceDir = "C:\CasaBlanca\admin-django\media",
    [int]$RetentionDays = 30
)

$ErrorActionPreference = "Stop"
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$dbBackupDir = Join-Path $BackupRoot "db"
$mediaBackupDir = Join-Path $BackupRoot "media"
$logFile = Join-Path $BackupRoot "backup-execution.log"

foreach ($dir in @($BackupRoot, $dbBackupDir, $mediaBackupDir)) {
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
}

function Write-BackupLog([string]$Message) {
    $line = "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] $Message"
    Add-Content -Path $logFile -Value $line
    Write-Host $line
}

Write-BackupLog "=== INICIO DE BACKUP AUTOMATICO (BD: $DbName) ==="

# 1. Localizar pg_dump
$pgDumpCmd = Get-Command "pg_dump" -ErrorAction SilentlyContinue
$pgDumpExe = if ($pgDumpCmd) { $pgDumpCmd.Source } else { "C:\Program Files\PostgreSQL\16\bin\pg_dump.exe" }

$dumpFile = Join-Path $dbBackupDir "${DbName}_${timestamp}.dump"
Write-BackupLog "Ejecutando pg_dump -Fc hacia: $dumpFile"

& $pgDumpExe -h $DbHost -p $DbPort -U $DbUser -Fc -f $dumpFile $DbName
if ($LASTEXITCODE -ne 0) {
    Write-BackupLog "ERROR: pg_dump finalizo con codigo $LASTEXITCODE"
    exit $LASTEXITCODE
}

$dumpInfo = Get-Item $dumpFile
$dumpHash = (Get-FileHash -Path $dumpFile -Algorithm SHA256).Hash
Write-BackupLog "OK: Dump generado ($($dumpInfo.Length) bytes) SHA256=$dumpHash"

# 2. Backup de carpeta media (imagenes del catalogo)
if (Test-Path $MediaSourceDir) {
    $mediaZip = Join-Path $mediaBackupDir "media_${timestamp}.zip"
    Compress-Archive -Path "$MediaSourceDir\*" -DestinationPath $mediaZip -Force
    $mediaInfo = Get-Item $mediaZip
    Write-BackupLog "OK: Backup de media completado ($($mediaInfo.Length) bytes) en $mediaZip"
} else {
    Write-BackupLog "INFO: Carpeta media ($MediaSourceDir) no encontrada en este entorno; omitiendo zip de media."
}

# 3. Politica de retencion (eliminar archivos mayores a $RetentionDays dias)
$cutoff = (Get-Date).AddDays(-$RetentionDays)
$expiredFiles = Get-ChildItem -Path $dbBackupDir, $mediaBackupDir -File | Where-Object { $_.LastWriteTime -lt $cutoff }
foreach ($old in $expiredFiles) {
    Remove-Item -Path $old.FullName -Force
    Write-BackupLog "RETENCION: Archivo antiguo eliminado ($($old.Name))"
}

Write-BackupLog "=== BACKUP FINALIZADO EXITOSAMENTE ==="
