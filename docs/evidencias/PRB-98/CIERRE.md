# Evidencia de Cierre — PRB-98: TLS con Caddy y CA local (casa-blanca.local — ADR-02)

- **Issue Jira:** `PRB-98` (Epic `PRB-105` — Tareas `B04`, `B09`)
- **Responsable:** Max Erick Muñoz Paredes
- **Rama:** `feature/PRB-98-tls-caddy-ca` → `develop`
- **Estado:** COMPLETADO Y VERIFICADO

---

## 1. Resumen de Implementación (ADR-02)

1. **Reverse Proxy TLS con Caddy (`scripts/deploy/Caddyfile`):**
   - Configura terminación HTTPS (`tls internal` / CA local) para `https://casa-blanca.local`, `https://192.168.1.10` y `https://localhost`.
   - Enruta `/api/*`, `/health` y `/version` hacia el backend Spring Boot (`127.0.0.1:8080`) propagando `X-Forwarded-Proto`, `X-Forwarded-Host` y `X-Real-IP`.
   - Enruta `/*` hacia el panel administrativo y KDS Django (`127.0.0.1:8000`).
2. **Soporte de Proxy HTTPS y CORS en Spring Boot (`application.yml` y `SecurityConfig.java`):**
   - Configurado `server.forward-headers-strategy: framework` para reconocimiento nativo de cabeceras `X-Forwarded-*` detrás de Caddy.
   - Incorporados los orígenes `https://casa-blanca.local` y `https://192.168.1.10` en `app.cors.allowed-origins`.
3. **Certificado Raíz CA (`certs/casa_blanca_ca.crt`) y Scripts de Despliegue (`scripts/deploy/setup-tls-caddy.ps1`, `install-windows-services.ps1`):**
   - Certificado CA X.509 PEM (`CN=Cevicheria La Casa Blanca Root CA`, SAN `DNS:casa-blanca.local, DNS:localhost, IP:192.168.1.10, IP:127.0.0.1`, huella SHA-256 `57:56:6D:57:D7:5F:EC:94:92:49:3B:76:98:EA:12:61:A4:AB:A4:1B:AC:F9:93:E2:B7:2E:01:6D:6D:02:7B:8C`) exportado en `certs/casa_blanca_ca.crt` para empaquetado como trust anchor en Android (`res/raw/casa_blanca_ca.crt`).
   - Servicio Windows `CasaBlancaCaddyTLS` (:443) y regla de Firewall `CasaBlanca-Caddy-HTTPS-443-LAN` (`TCP 443` restringido a `192.168.1.0/24`).

---

## 2. Archivos de Evidencia Cruda Adjuntos

- `docs/evidencias/PRB-98/01-tests.txt`: Salida cruda de `.\mvnw.cmd test` (`118` tests ejecutados, `0` fallos, incluyendo `corsYCabecerasProxyCaddyPermitenHttpsCasaBlancaLocal`).
- `docs/evidencias/PRB-98/02-git.txt`: Salida cruda de `git log --oneline -5`.
- `docs/evidencias/PRB-98/03-ca-cert.txt`: Salida cruda de `keytool -printcert -v -file certs/casa_blanca_ca.crt`.
