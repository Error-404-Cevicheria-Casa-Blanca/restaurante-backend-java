<#
.SYNOPSIS
    Instala y configura los servicios Windows de produccion para Restaurante Casa Blanca (PRB-65).
.DESCRIPTION
    Configura inicio automatico en Windows para:
      1. PostgreSQL 16 (CasaBlancaPostgres / postgresql-x64-16)
      2. Backend Spring Boot (CasaBlancaBackendJava en puerto 8080)
      3. Panel Admin / KDS Django (CasaBlancaAdminDjango en puerto 8000)
    Ademas configura las reglas del Firewall de Windows restringidas a la subred LAN (192.168.1.0/24).
#>

param(
    [string]$InstallRoot = "C:\CasaBlanca",
    [string]$NssmPath = "C:\CasaBlanca\tools\nssm.exe",
    [string]$JavaExe = "C:\Program Files\Eclipse Adoptium\jdk-17\bin\java.exe",
    [string]$PythonExe = "C:\CasaBlanca\admin-django\venv\Scripts\python.exe",
    [string]$LanSubnet = "192.168.1.0/24"
)

$ErrorActionPreference = "Stop"

Write-Host "=== [PRB-65] Instalacion de Servicios Windows - Restaurante Casa Blanca ===" -ForegroundColor Cyan

# 1. Crear estructura de directorios de produccion
$dirs = @(
    "$InstallRoot\backend",
    "$InstallRoot\admin-django",
    "$InstallRoot\logs",
    "$InstallRoot\backups\db",
    "$InstallRoot\backups\media",
    "$InstallRoot\releases"
)
foreach ($d in $dirs) {
    if (-not (Test-Path $d)) {
        New-Item -ItemType Directory -Path $d -Force | Out-Null
        Write-Host "[OK] Directorio creado: $d"
    }
}

# 2. Configurar servicio PostgreSQL con inicio automatico y recuperacion ante fallos
$pgService = Get-Service -Name "postgresql*" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($pgService) {
    Set-Service -Name $pgService.Name -StartupType Automatic
    sc.exe failure $pgService.Name reset= 86400 actions= restart/5000/restart/10000/restart/30000 | Out-Null
    Write-Host "[OK] Servicio PostgreSQL ($($pgService.Name)) configurado en inicio Automatico." -ForegroundColor Green
} else {
    Write-Warning "No se encontro servicio postgresql* instalado; verifique la instalacion de PostgreSQL 16."
}

# 3. Registrar servicio CasaBlancaBackendJava (:8080) via NSSM
if (Test-Path $NssmPath) {
    & $NssmPath install CasaBlancaBackendJava $JavaExe "-Xms256m -Xmx512m -jar $InstallRoot\backend\restaurante-backend.jar"
    & $NssmPath set CasaBlancaBackendJava AppDirectory "$InstallRoot\backend"
    & $NssmPath set CasaBlancaBackendJava DependOnService $pgService.Name
    & $NssmPath set CasaBlancaBackendJava Start SERVICE_AUTO_START
    & $NssmPath set CasaBlancaBackendJava AppStdout "$InstallRoot\logs\service-backend-stdout.log"
    & $NssmPath set CasaBlancaBackendJava AppStderr "$InstallRoot\logs\service-backend-stderr.log"
    & $NssmPath set CasaBlancaBackendJava AppExit Default Restart
    & $NssmPath set CasaBlancaBackendJava AppRestartDelay 5000
    Write-Host "[OK] Servicio CasaBlancaBackendJava (:8080) registrado con auto-restart." -ForegroundColor Green

    # 4. Registrar servicio CasaBlancaAdminDjango (:8000) via NSSM + Waitress WSGI
    & $NssmPath install CasaBlancaAdminDjango $PythonExe "-m waitress --host=0.0.0.0 --port=8000 config.wsgi:application"
    & $NssmPath set CasaBlancaAdminDjango AppDirectory "$InstallRoot\admin-django"
    & $NssmPath set CasaBlancaAdminDjango DependOnService CasaBlancaBackendJava
    & $NssmPath set CasaBlancaAdminDjango Start SERVICE_AUTO_START
    & $NssmPath set CasaBlancaAdminDjango AppStdout "$InstallRoot\logs\service-django-stdout.log"
    & $NssmPath set CasaBlancaAdminDjango AppStderr "$InstallRoot\logs\service-django-stderr.log"
    & $NssmPath set CasaBlancaAdminDjango AppExit Default Restart
    & $NssmPath set CasaBlancaAdminDjango AppRestartDelay 5000
    Write-Host "[OK] Servicio CasaBlancaAdminDjango (:8000) registrado con auto-restart." -ForegroundColor Green
} else {
    Write-Host "[INFO] NSSM no encontrado en $NssmPath. Registrando definicion alternativa mediante sc.exe..."
    $backendBin = "`"$JavaExe`" -jar `"$InstallRoot\backend\restaurante-backend.jar`""
    sc.exe create CasaBlancaBackendJava binPath= $backendBin start= auto DisplayName= "Casa Blanca Backend Java" | Out-Null
    $djangoBin = "`"$PythonExe`" -m waitress --host=0.0.0.0 --port=8000 config.wsgi:application"
    sc.exe create CasaBlancaAdminDjango binPath= $djangoBin start= auto DisplayName= "Casa Blanca Admin Django" | Out-Null
    Write-Host "[OK] Servicios creados con sc.exe (fallback sin NSSM)." -ForegroundColor Green
}

# 5. Configurar Firewall de Windows para permitir solo subred LAN en puertos 8080 y 8000
$rules = @(
    @{ Name = "CasaBlanca-Backend-8080-LAN"; Port = 8080; Desc = "API REST Spring Boot POS Casa Blanca (Solo LAN)" },
    @{ Name = "CasaBlanca-Django-8000-LAN";  Port = 8000; Desc = "Panel Admin y KDS Django Casa Blanca (Solo LAN)" }
)

foreach ($r in $rules) {
    Remove-NetFirewallRule -DisplayName $r.Name -ErrorAction SilentlyContinue
    New-NetFirewallRule -DisplayName $r.Name `
        -Direction Inbound `
        -Protocol TCP `
        -LocalPort $r.Port `
        -RemoteAddress $LanSubnet `
        -Action Allow `
        -Profile Private,Domain `
        -Description $r.Desc | Out-Null
    Write-Host "[OK] Regla de Firewall aplicada: $($r.Name) (TCP $($r.Port) -> $LanSubnet)" -ForegroundColor Green
}

Write-Host "=== Configuracion de servicios y Firewall completada ===" -ForegroundColor Cyan
