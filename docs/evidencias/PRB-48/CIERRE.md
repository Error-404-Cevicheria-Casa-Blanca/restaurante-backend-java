# Evidencia de Cierre — PRB-48: Release v0.1.0 con tag y build reproducible

- **Issue Jira:** `PRB-48`
- **Tag publicado en los 3 repositorios:** `v0.1.0`
- **Estado:** COMPLETADO Y VERIFICADO

---

## 1. Tags Publicados en la Organización GitHub (`Error-404-Cevicheria-Casa-Blanca`)

| Repositorio | Tag | Comando de Verificación Reproducible | Artefacto Generado |
|---|---|---|---|
| `restaurante-backend-java` | `v0.1.0` | `git checkout v0.1.0 && mvn clean package` | `target/backend-0.0.1-SNAPSHOT.jar` |
| `restaurante-admin-django` | `v0.1.0` | `git checkout v0.1.0 && python manage.py test` | Panel Web Backoffice + Migraciones SQL |
| `restaurante-app-kotlin` | `v0.1.0` | `git checkout v0.1.0 && ./gradlew assembleDebug testDebugUnitTest` | APK Android para Tablets de Salón |

---

## 2. Credenciales de Prueba Documentadas en `README.md`

- **Mozo de Salón:** usuario `mozo_01` / contraseña `password123` / PIN `1234`
- **Cajero:** usuario `cajero_01` / contraseña `password123` / PIN `3456`
- **Cocinero (KDS):** usuario `cocinero_01` / contraseña `password123` / PIN `2345`
- **Administrador:** usuario `admin_01` / contraseña `password123` / PIN `9999`
