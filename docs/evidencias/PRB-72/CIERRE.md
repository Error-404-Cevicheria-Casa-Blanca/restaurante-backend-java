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
   - Certificado de firma de producción (fuera de Git): `CN=Cevicheria La Casa Blanca, OU=Equipo Error 404, O=La Casa Blanca, L=Cajamarca, ST=Cajamarca, C=PE` (RSA 2048 bits), huella SHA-256: `1948fc9e38d8436cd4dfdf4c9d0689667293f2204baa4edafb12eb426e5cf30c`.

## 2. Evidencias de Compilación y Verificación de APK (Verificadas)
- [x] `docs/evidencias/PRB-72/00-backend-verify.txt`: `mvn clean verify` (`117/117` pruebas pasando, Checkstyle `0` violaciones, SpotBugs `0` bugs).
- [x] `docs/evidencias/PRB-72/05-buildconfig-release.txt`: `BuildConfig.java` de release (`VERSION_CODE = 2`, `VERSION_NAME = "2.0.0"`, `BASE_URL = "http://192.168.1.10:8080/"`, `USE_FAKE_DATA = false`, `BYPASS_TUNNEL = false`).
- [x] `docs/evidencias/PRB-72/06-apksigner.txt`: Verificación de firma v2 con certificado de producción (`apksigner verify --verbose --print-certs`).
- [x] `docs/evidencias/PRB-72/07-aapt-badging.txt`: Metadatos del APK (`versionCode='2'`, `versionName='2.0.0'`).
- [x] `docs/evidencias/PRB-72/08-manifest-release.txt`: Manifiesto compilado con `android:networkSecurityConfig` y sin `usesCleartextTraffic="true"`.
- [x] `docs/evidencias/PRB-72/09-network-security-config.txt`: XML compilado (`res/8G.xml` / `xml/network_security_config`) con `base-config cleartextTrafficPermitted="false"` y `domain-config` exclusivo para `192.168.1.10`.

## 3. Salidas Crudas de Rollback Pendientes (Entorno Windows / PostgreSQL / Tablet Física)
- [ ] `docs/evidencias/PRB-72/01-rollback.txt`: Salida de ejecución de `rollback-release.ps1`.
- [ ] `docs/evidencias/PRB-72/02-restore.txt`: Salida de `restore-casablanca.ps1` sobre la BD destino `casablanca_rollback_test` / `casablanca`.
- [ ] `docs/evidencias/PRB-72/03-health.txt`: Verificación de `GET /api/v1/health` post-rollback (`status=ok, db=connected`).
- [ ] `docs/evidencias/PRB-72/04-apk-rollback.txt`: Salida de `adb install -r -d` y `adb shell dumpsys package` confirmando `versionCode`.
- [ ] `docs/evidencias/PRB-72/05-db-check.txt`: Verificación de tablas en `casablanca_rollback_test`.
