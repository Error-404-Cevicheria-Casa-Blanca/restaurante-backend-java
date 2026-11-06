# Evidencia de Cierre — PRB-65: [P-19] Despliegue en PC central como servicios Windows

- **Issue Jira:** `PRB-65`
- **Responsable:** Max Erick Muñoz Paredes
- **Rama:** `feature/PRB-65-infraestructura-y-produccion`
- **Estado:** COMPLETADO Y VERIFICADO

---

## 1. Entregables Implementados

1. **Variables de Entorno de Producción (`.env.example`):**
   - Documenta `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRATION_MS=43200000`, `CORS_ALLOWED_ORIGINS` y `LOG_PATH=C:/CasaBlanca/logs` sin exponer credenciales en Git.
2. **Script de Instalación de Servicios Windows y Firewall LAN (`scripts/deploy/install-windows-services.ps1`):**
   - Configura inicio automático (`Automatic`) y recuperación ante fallos (`sc.exe failure ... restart/5000`) para PostgreSQL 16.
   - Registra el servicio `CasaBlancaBackendJava` (`:8080`) con dependencia en PostgreSQL y auto-restart (`5000 ms`).
   - Registra el servicio `CasaBlancaAdminDjango` (`:8000` vía Waitress WSGI) con dependencia en el backend.
   - Configura reglas de Firewall de Windows (`CasaBlanca-Backend-8080-LAN` y `CasaBlanca-Django-8000-LAN`) restringidas exclusivamente a la subred LAN `192.168.1.0/24`.
3. **Procedimiento Automatizado de Rollback (`scripts/deploy/rollback-release.ps1`):**
   - Detiene `CasaBlancaBackendJava`, respalda el `.jar` fallido, restaura el artefacto `.jar` previo desde `C:\CasaBlanca\releases`, ejecuta restore opcional de base de datos y valida automáticamente `GET /api/v1/health`.

---

## 2. Verificación de Criterios de Aceptación

| Criterio de Aceptación | Estado | Evidencia |
|---|---|---|
| PostgreSQL, Backend Java (`:8080`) y Django (`:8000`) configurados como servicios Windows con inicio automático | CUMPLE | `scripts/deploy/install-windows-services.ps1` |
| Firewall de Windows permite acceso en `:8080` y `:8000` solo a la subred LAN (`192.168.1.0/24`) | CUMPLE | Reglas `New-NetFirewallRule -RemoteAddress 192.168.1.0/24` |
| Secretos y credenciales en variables de entorno fuera del repositorio | CUMPLE | `.env.example` + `application.yml` con placeholders `${...}` |
| Procedimiento de rollback a versión anterior documentado y automatizado | CUMPLE | `scripts/deploy/rollback-release.ps1` |
