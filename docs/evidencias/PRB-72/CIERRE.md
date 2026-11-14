# Evidencia de Cierre — PRB-72: [P-26] Release de producción con APK firmado y rollback

- **Issue Jira:** `PRB-72`
- **Responsable:** Julio Alexander Pérez Muñoz
- **Rama:** `develop`
- **Estado:** PENDIENTE DE VERIFICACIÓN (A la espera de ejecución de pruebas de rollback con salida cruda de terminal y generación de APK release firmado)

---

## 1. Resumen de Entregables Técnicos

1. **Documento de Release, Checklist de Go-Live y Acta de War Room (`docs/produccion/RELEASE-GO-LIVE-Y-WAR-ROOM.md`):**
   - Especificación de artefactos reproducibles (`v2.0.0-prod`: `.jar`, `.apk`, `.dump`).
   - Procedimiento de rollback parametrizado (`scripts/deploy/rollback-release.ps1` hacia base de datos `casablanca` y reinstalación de APK previo vía ADB).
2. **Configuración de Red Segura y Build Android (`restaurante-app-kotlin`):**
   - `network_security_config.xml` configurado para restringir cleartext traffic exclusivamente hacia `192.168.1.10`.
   - `build.gradle.kts` actualizado con `versionCode = 2`, `versionName = "2.0.0"` y `baseUrlRelease = "http://192.168.1.10:8080/"`.

## 2. Salidas Crudas Requeridas para Cierre Formal
- [ ] `docs/evidencias/PRB-72/01-rollback.txt`: Salida de ejecución de `rollback-release.ps1`.
- [ ] `docs/evidencias/PRB-72/02-restore.txt`: Salida de `restore-casablanca.ps1` sobre la BD destino `casablanca`.
- [ ] `docs/evidencias/PRB-72/03-health.txt`: Verificación de `GET /api/v1/health` post-rollback (`status=ok, db=connected`).
- [ ] `docs/evidencias/PRB-72/04-apk-rollback.txt`: Salida de `adb install -r -d` y `adb shell dumpsys package` confirmando `versionCode`.
