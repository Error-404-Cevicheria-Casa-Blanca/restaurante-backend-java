# Cierre de Tarea — PRB-64 ([P-18] Healthchecks y logs rotativos) + Persistencia JPA de `pago` y `sesion_caja` (PRB-51)

## 1. Resumen Ejecutivo
Se implementaron los endpoints públicos de monitoreo y versión en la LAN (`/api/v1/health`, `/health`, `/api/v1/version`, `/version`) con verificación activa de conectividad JDBC a la base de datos (`conn.isValid(2)`), la configuración de logs rotativos (`logback-spring.xml`) con política de retención de 30 días y tope de 500 MB, y el mapeo JPA transaccional de las tablas `pago` (`Pago.java`) y `sesion_caja` (`SesionCaja.java`) sincronizadas con la migración `V2__produccion.sql` (`PRB-51`).

## 2. Componentes Implementados
- **`HealthController` (`/api/v1/health`, `/health`, `/api/v1/version`, `/version`)**:
  - Consulta la salud de la conexión a PostgreSQL/H2 mediante `DataSource` y expone `{status, db, version, uptime_seg}` con HTTP `200 OK` cuando la base de datos responde o `503 Service Unavailable` en caso de caída.
  - Expone `/api/v1/version` y `/version` con metadatos de versión y servicio para monitoreo desde el panel backoffice.
- **Logs rotativos (`src/main/resources/logback-spring.xml`)**:
  - Configura `RollingFileAppender` con `SizeAndTimeBasedRollingPolicy` hacia `logs/casablanca-backend.log` y archivos comprimidos `logs/casablanca-backend.%d{yyyy-MM-dd}.%i.log.gz` (`maxFileSize = 10MB`, `maxHistory = 30`, `totalSizeCap = 500MB`).
- **Persistencia en `pago` y `sesion_caja` (`Pago.java`, `SesionCaja.java`, `CobroService.java`)**:
  - Mapeo 100% compatible con `ddl-auto: validate` contra `V2__produccion.sql` (`PRB-51`).
  - Cada cobro de mesa o pedido inserta los registros en la tabla `pago` con idempotencia por `client_request_id` y actualiza los acumulados de `sesion_caja` si existe una sesión abierta.

## 3. Pruebas Automatizadas y Cobertura
- Se añadieron **3 pruebas de integración** en `HealthCheckIntegrationTest` y validación de persistencia en `PagoRepository` dentro de `MesaCobroIntegrationTest`.
- **Total suite ejecutada:** 79 tests en verde (0 fallos, 0 errores).
- **Cobertura JaCoCo:** 82.13% de líneas / 83.53% de instrucciones.
- **Checkstyle / SpotBugs:** 0 violaciones / 0 bugs.
