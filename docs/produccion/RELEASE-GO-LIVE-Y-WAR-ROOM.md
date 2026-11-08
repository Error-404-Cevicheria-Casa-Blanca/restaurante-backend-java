# Release de Producción, Checklist de Go-Live y Acta de War Room — PRB-72

- **Versión de Release:** `v2.0.0-prod`
- **Fecha de Corte / Go-Live:** Noviembre 2026
- **Ventana de War Room:** 21/11 al 24/11

---

## 1. Artefactos Reproducibles del Release `v2.0.0-prod`

| Componente | Artefacto Generado | Comando Reproducible | Ubicación en Producción |
|---|---|---|---|
| **Backend Spring Boot 3.3 (Java 17)** | `backend-0.0.1-SNAPSHOT.jar` (`v2.0.0-prod`) | `mvn clean package -DskipTests=false` (`92/92` tests verdes, Checkstyle `0`, SpotBugs `0`, JaCoCo `> 82%`) | `C:\CasaBlanca\backend\restaurante-backend.jar` |
| **Panel Admin & KDS Django** | Release `v2.0.0-prod` + `V2__produccion.sql` | `python manage.py check --deploy` + Waitress `:8000` | `C:\CasaBlanca\admin-django\` |
| **App Tablets Android (Kotlin)** | `app-release-signed.apk` (`v2.0.0-prod`) | `./gradlew assembleRelease` (firmado con keystore de producción) | Instalado en las 2 tablets de salón |
| **Base de Datos PostgreSQL 16** | Snapshot pre-release `casablanca_pre_golive.dump` | `scripts/backup/backup-casablanca.ps1` | `C:\CasaBlanca\backups\db\` |

---

## 2. Simulacro de Rollback Verificado

Se validó el procedimiento completo de vuelta atrás (`scripts/deploy/rollback-release.ps1`):
1. Detención controlada del servicio `CasaBlancaBackendJava`.
2. Restauración del binario `.jar` anterior desde `C:\CasaBlanca\releases\` y restauración de base de datos con `scripts/backup/restore-casablanca.ps1`.
3. Reinstalación de versión previa de `.apk` en tablet de respaldo (`adb install -r -d`).
4. Verificación automática de `GET /api/v1/health` (`status: "ok"`, `db: "connected"`) en `< 10 segundos`.

---

## 3. Checklist Final de Go-Live

- [x] Esquema PostgreSQL v2 (`V2__produccion.sql` con `pago` y `sesion_caja`) validado con `ddl-auto: validate`.
- [x] `92/92` pruebas unitarias y de integración pasando con `0` violaciones Checkstyle, `0` bugs SpotBugs y cobertura JaCoCo `> 82%`.
- [x] Servicios Windows (`CasaBlancaPostgres`, `CasaBlancaBackendJava`, `CasaBlancaAdminDjango`) en inicio automático.
- [x] Firewall de Windows restringido a la LAN del local (`192.168.1.0/24`) y CORS restringido al origen del panel.
- [x] Backups diarios programados a las `23:30` con retención de 30 días y restore probado.
- [x] Pruebas de carga y caos superadas (`p95 = 64 ms < 300 ms`, `0` duplicados).
- [x] UAT firmada por el dueño y capacitación operativa completada con manual impreso en caja.

---

## 4. Plan de Guardia — War Room (21/11 al 24/11)

| Día | Turno Almuerzo (11:00 - 16:30) | Turno Cena / Cierre (18:00 - 23:30) | Acción de Control |
|---|---|---|---|
| **Día 1 (21/11)** | Guardia presencial en salón (Max + Franco) | Monitoreo de cierre de caja y primer backup `23:30` (Max + Jerardo) | Verificación de latencia LAN, impresión térmica y cuadre de caja |
| **Día 2 (22/11)** | Guardia en hora punta de fin de semana | Revisión de logs rotativos (`logs/restaurante-backend.log`) y arqueo | Auditoría de `0` errores `5xx` y tiempos de despacho KDS |
| **Día 3 (23/11)** | Soporte activo en pico dominical | Verificación de sesiones de caja y pagos mixtos | Confirmación de estabilidad bajo máxima demanda |
| **Día 4 (24/11)** | Revisión post-fin de semana | Firma de cierre de estabilización | Paso a soporte operativo regular |
