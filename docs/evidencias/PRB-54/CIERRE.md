# Cierre de Tarea — PRB-54 ([P-07] Roles, PIN y control de acceso)

## 1. Resumen Ejecutivo
Se implementó la autenticación por PIN de 4 dígitos con hash BCrypt (`POST /api/v1/auth/pin`), el bloqueo temporal de cuenta por 15 minutos tras 5 intentos fallidos (`423 Locked`), el cierre de sesión con revocación de JWT (`POST /api/v1/auth/logout`), el turno JWT de 12 horas por defecto y la matriz completa de control de acceso por rol (`MOZO`, `COCINERO`, `CAJERO`, `ADMIN`).

## 2. Endpoints y Reglas de Seguridad Implementadas
- **`POST /api/v1/auth/pin`**:
  - Recibe `{usuario, pin}` validando que el PIN contenga exactamente 4 dígitos numéricos (`^\d{4}$`).
  - Verifica el PIN contra su hash BCrypt (`PasswordEncoder`) y devuelve `{token, tipo, usuario:{id, nombre, rol}}`.
- **`POST /api/v1/auth/login`**:
  - Autenticación por credenciales `{usuario, clave}` integrada con el mismo control de intentos fallidos y bloqueo temporal.
- **Bloqueo temporal tras 5 intentos fallidos (`423 Locked`)**:
  - `AuthService` contabiliza intentos fallidos por usuario (tanto por PIN como por clave).
  - Al alcanzar 5 intentos fallidos consecutivos, bloquea temporalmente la cuenta durante 15 minutos y lanza `CuentaBloqueadaException`, mapeada en `GlobalExceptionHandler` a HTTP `423 Locked` con `{"mensaje": "...", "codigo": "CUENTA_BLOQUEADA"}`.
- **`POST /api/v1/auth/logout`**:
  - Revoca el token JWT actual (`jti` único por emisión en `JwtUtil`) y responde `204 No Content`. Cualquier intento posterior de usar el token revocado es rechazado por `JwtAuthenticationFilter` con `401 Unauthorized`.
- **Matriz de permisos por rol (`SecurityConfig`)**:
  - `MOZO`: apertura/cambio de estado de mesas, creación y anulación de pedidos, consulta de cuenta y pre-cuenta.
  - `COCINERO`: consulta de comandas y cambio de estado de preparación; denegado (`403 Forbidden`) en creación/anulación de pedidos, cambio de estado de mesas y cobros.
  - `CAJERO`: consulta de cuenta, pre-cuenta, cobro multi-medio, pagos parciales, sesiones de caja y reportes; denegado (`403 Forbidden`) en creación de pedidos y apertura manual de mesas.
  - `ADMIN`: acceso total a todos los módulos operativos y administrativos (incluyendo inventario).
- **Aislamiento de credenciales de prueba**:
  - `DataInitializer` se mantiene estrictamente restringido a `@Profile("test")` (base de datos H2 en memoria para pruebas automatizadas) y `application.yml` opera exclusivamente mediante variables de entorno con turno JWT por defecto de 12 horas (`43200000` ms).

## 3. Pruebas Automatizadas y Cobertura
- Se añadieron **5 pruebas integrales** en `AuthRolesPinIntegrationTest`:
  1. `loginPorPinExitosoParaTodosLosRoles`
  2. `loginPorPinFormatoInvalidoRetorna400`
  3. `bloqueoTemporalTras5IntentosFallidosRetorna423`
  4. `logoutRevocaTokenCorrectamente`
  5. `matrizDePermisosPorRol`
- **Total suite ejecutada:** 76 tests en verde (0 fallos, 0 errores).
- **Cobertura JaCoCo:** 84.54% de líneas / 85.19% de instrucciones.
- **Checkstyle / SpotBugs:** 0 violaciones / 0 bugs.
