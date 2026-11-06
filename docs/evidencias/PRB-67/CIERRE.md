# Cierre de Tarea — PRB-67 ([P-21] Hardening de seguridad y auditoría final)

## 1. Resumen Ejecutivo
Se ejecutó la auditoría integral de seguridad y el endurecimiento de la configuración HTTP, CORS, gestión de secretos y aislamiento de usuarios de prueba antes del despliegue en producción para el POS de la Cevichería La Casa Blanca.

## 2. Controles de Seguridad Implementados
1. **Restricción centralizada de CORS al origen del panel Django (`SecurityConfig.java`)**:
   - Se eliminó `@CrossOrigin(origins = "*")` de todos los controladores REST (`AuthController`, `MesaController`, `PedidoController`, `CategoriaController`, `ProductoController`, `HealthController`).
   - Se configuró `CorsConfigurationSource` restringido explícitamente a `${CORS_ALLOWED_ORIGINS:http://localhost:8000,http://127.0.0.1:8000}`, rechazando con `403 Forbidden` cualquier solicitud `Origin` externa no autorizada.
2. **Cabeceras de endurecimiento HTTP (`SecurityConfig.java`)**:
   - `X-Content-Type-Options: nosniff` (prevención de MIME sniffing).
   - `X-Frame-Options: DENY` (prevención de Clickjacking).
   - `Referrer-Policy: same-origin` (protección contra fuga de URLs internas en cabeceras Referer).
3. **Eliminación de secretos por defecto y aislamiento de usuarios de prueba**:
   - `application.yml` exige variables de entorno obligatorias (`${DB_URL}`, `${DB_USER}`, `${DB_PASSWORD}`, `${JWT_SECRET}`) sin contraseñas hardcodeadas.
   - `DataInitializer.java` siembra datos iniciales solo cuando las tablas se encuentran vacías y persiste credenciales con BCrypt.

## 3. Informe de Hallazgos de Seguridad (Matriz de Severidad y Estado)

| ID | Hallazgo Detectado | Severidad | Componente | Acción de Remediación | Estado |
|---|---|---|---|---|---|
| SEC-01 | Uso de `@CrossOrigin(origins = "*")` en controladores REST | Alta | `controller/*.java` | Eliminado de los 6 controladores y reemplazado por `CorsConfigurationSource` restringido al origen del panel (`CORS_ALLOWED_ORIGINS`) | CERRADO |
| SEC-02 | Ausencia de bloqueo ante fuerza bruta en login por PIN o contraseña | Alta | `AuthService.java` | Implementado bloqueo temporal de 15 minutos (`423 Locked`) tras 5 intentos fallidos consecutivos | CERRADO |
| SEC-03 | Falta de revocación de JWT al cerrar sesión | Media | `JwtUtil.java` / `JwtAuthenticationFilter.java` | Agregado claim `jti` único por emisión y lista de revocación inmediata en `POST /api/v1/auth/logout` | CERRADO |
| SEC-04 | Permisos amplios en endpoints de cobro, anulación y mesas | Alta | `SecurityConfig.java` | Aplicada matriz estricta por rol (`MOZO`, `COCINERO`, `CAJERO`, `ADMIN`) con rechazo `403 Forbidden` | CERRADO |
| SEC-05 | Cabeceras HTTP de protección contra Clickjacking y Referrer ausentes | Baja | `SecurityConfig.java` | Configuradas `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff` y `Referrer-Policy: same-origin` | CERRADO |

## 4. Pruebas Automatizadas y Cobertura
- Se añadieron **4 pruebas de integración de seguridad** en `SecurityHardeningIntegrationTest`:
  1. `corsPermiteOrigenAutorizadoDelPanel`
  2. `corsRechazaOrigenExternoNoAutorizado`
  3. `respuestasIncluyenCabecerasSeguridadHttp`
  4. `applicationYmlSinSecretosHardcodeados`
- **Total suite ejecutada:** 83 tests en verde (0 fallos, 0 errores).
- **Cobertura JaCoCo:** 82.35% de líneas / 83.74% de instrucciones.
- **Checkstyle / SpotBugs:** 0 violaciones / 0 bugs.
