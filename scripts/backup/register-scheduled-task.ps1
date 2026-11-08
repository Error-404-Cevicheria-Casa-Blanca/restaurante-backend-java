<#
.SYNOPSIS
    Registra la tarea programada diaria en Windows Task Scheduler para el backup de Casa Blanca (PRB-66).
.DESCRIPTION
    Crea la tarea 'CasaBlanca-DailyBackup' para ejecutarse todos los dias a las 23:30 (cierre de turno).
#>

param(
    [string]$TaskName = "CasaBlanca-DailyBackup",
    [string]$DailyTime = "23:30",
    [string]$BackupScript = "$PSScriptRoot\backup-casablanca.ps1"
)

$ErrorActionPreference = "Stop"

$action = New-ScheduledTaskAction -Execute "powershell.exe" `
    -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$BackupScript`""
$trigger = New-ScheduledTaskTrigger -Daily -At $DailyTime
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -DontStopIfGoingOnBatteries -AllowStartIfOnBatteries

Register-ScheduledTask -TaskName $TaskName `
    -Action $action `
    -Trigger $trigger `
    -Settings $settings `
    -Description "Backup diario automatico de PostgreSQL (casablanca) y media del catalogo a las 23:30 (PRB-66)." `
    -Force | Out-Null

Write-Host "[OK] Tarea programada '$TaskName' registrada exitosamente para ejecutarse a las $DailyTime." -ForegroundColor Green
