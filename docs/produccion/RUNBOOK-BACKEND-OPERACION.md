# Runbook Operativo del Backend, TLS y Rollback — Restaurante Casa Blanca (B08 / PRB-65 / PRB-98 / PRB-106)

- **Fecha de actualización:** 2026-11-20
- **Responsable:** Max Erick Muñoz Paredes (Backend Lead / DevOps)
- **Alcance:** PC Central Windows (Servidor Local LAN `192.168.1.10` / `https://casa-blanca.local`)

---

## 1. Arquitectura de Servicios en la PC Central

| Orden | Servicio Windows | Puerto | Función | Dependencia |
|---|---|---|---|---|
| 1 | `postgresql-x64-16` / `18` | `5432` | Base de datos transaccional `casablanca` | — |
| 2 | `CasaBlancaBackendJava` | `8080` | API REST Spring Boot (`/api/v1/*`, `/health`, `/version`) | PostgreSQL |
| 3 | `CasaBlancaAdminDjango` | `8000` | Panel Administrativo Web, Caja, Stock y KDS Cocina/Barra | Backend Java + PostgreSQL |
| 4 | `CasaBlancaImpresionWorker` | TCP `9100` | Worker `manage.py despachar_impresion --loop` (ESC/POS 80 mm) | PostgreSQL + Impresora LAN |
| 5 | `CasaBlancaCaddyTLS` | `443` | Reverse Proxy HTTPS (`https://casa-blanca.local`) | Backend `:8080` + Django `:8000` |

---

## 2. Arranque y Verificación Diaria (Apertura de Local)

```powershell
# 1. Verificar estado de los servicios Windows
Get-Service postgresql*, CasaBlanca* | Select-Object Name, Status, StartType

# 2. Verificar Healthcheck del Backend Java (directo y vía Caddy HTTPS)
Invoke-RestMethod http://localhost:8080/api/v1/health
curl.exe --cacert C:\CasaBlanca\backend\certs\casa_blanca_ca.crt https://casa-blanca.local/api/v1/health

# 3. Verificar versión activa
Invoke-RestMethod http://localhost:8080/api/v1/version
```
- **Resultado esperado:** `{"status":"UP","db":"UP","version":"2.0.0-prod",...}`.

---

## 3. Gestión de Certificados TLS y Caddy (`PRB-98` / ADR-02)

```powershell
# Exportar CA local y sincronizar trust anchor con proyecto Android
.\scripts\deploy\setup-tls-caddy.ps1 -ServerLanIp "192.168.1.10" -Hostname "casa-blanca.local"

# Recargar configuración del Caddyfile sin corte de conexiones
C:\CasaBlanca\tools\caddy.exe reload --config C:\CasaBlanca\backend\Caddyfile
```
- El certificado raíz se almacena en `certs/casa_blanca_ca.crt` y en `app/src/main/res/raw/casa_blanca_ca.crt`.

---

## 4. Cola de Impresión Térmica 80 mm (`PRB-106` / `PRB-107`)

- **Encolado desde Java (`PRB-106`):**
  - Al crear o ampliar un pedido (`POST /api/v1/pedidos`), Java inserta en `impresion_cola` una fila `COMANDA` por cada estación (`BARRA_FRIA`, `COCINA_CALIENTE`, `BEBIDAS`, `POSTRES`) con `estado = 'PENDIENTE'`.
  - Al solicitar o reimprimir una pre-cuenta (`POST /api/v1/mesas/{id}/precuenta`), Java inserta una fila `PRECUENTA` con `estado = 'PENDIENTE'` y `payload = '{"mesa_id": X}'`.
- **Despacho desde Worker Django (`PRB-107` / `PRB-108`):**
  - El servicio `CasaBlancaImpresionWorker` procesa en bucle las filas `PENDIENTE`, genera la secuencia ESC/POS de 48 columnas y actualiza `estado` a `IMPRESO` o `FALLIDO` (tras 3 intentos).

---

## 5. Backups Automáticos y Restauración (`PRB-66`)

```powershell
# Ejecutar backup manual inmediato (BD + media)
.\scripts\backup\backup-casablanca.ps1

# Probar restauración en base de datos aislada de validación
.\scripts\backup\restore-casablanca.ps1 `
  -DumpFile "C:\CasaBlanca\backups\db\casablanca_latest.dump" `
  -TargetDb "casablanca_rollback_test"
```

---

## 6. Procedimiento de Rollback de Emergencia (`PRB-65` / `PRB-72`)

Si una actualización presenta fallos críticos durante el Go-Live:

```powershell
.\scripts\deploy\rollback-release.ps1 `
  -PreviousJarPath "C:\CasaBlanca\releases\restaurante-backend-prev.jar" `
  -PreviousApkPath "C:\CasaBlanca\releases\app-release-prev.apk" `
  -DbDumpPath "C:\CasaBlanca\backups\db\casablanca_pre_golive.dump" `
  -TargetDb "casablanca"
```
El script detiene `CasaBlancaBackendJava`, respalda el `.jar` fallido, restaura el `.jar` previo, ejecuta `pg_restore` sobre `TargetDb`, reinstala el APK previo por ADB (si hay tablet conectada), levanta el servicio y valida `GET /api/v1/health`.
