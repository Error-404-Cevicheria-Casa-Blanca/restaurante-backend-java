# Cierre de Tarea — PE-9 (HU-01: Autenticación JWT y roles)

- **Rama:** `feature/PE-9-auth-jwt`
- **Paquete base:** `com.lacasablanca.backend`
- **Dependencias:** Spring Security 6, JJWT `0.13.0`, BCryptPasswordEncoder.

## Cumplimiento del Contrato (`API-v1.md`) y Reglas Técnicas
1. **Request de Login (`LoginRequest`):** recibe `{"usuario": "...", "clave": "..."}` validado con `@NotBlank`.
2. **Response de Login (`LoginResponse` + `UsuarioDto`):** retorna HTTP 200 con objeto anidado `{"token": "...", "tipo": "Bearer", "usuario": {"id": 1, "nombre": "...", "rol": "MOZO"}}`.
3. **Claims del JWT (`JwtUtil`):** `sub` = id del usuario, `rol` = nombre del rol, firmado con HMAC-SHA usando `${app.jwt.secret}` y `${app.jwt.expiration-ms}`.
4. **Error 401 genérico (`AuthService` + `GlobalExceptionHandler`):** responde `{"mensaje": "Usuario o contraseña incorrectos"}` tanto para usuario inexistente como para contraseña incorrecta.
5. **Token expirado -> HTTP 403 (`JwtAuthenticationFilter` - Regla D9):** captura `ExpiredJwtException` y responde HTTP 403 con `{"mensaje": "Token JWT expirado"}`.
6. **Pruebas unitarias y de integración:**
   - `AuthServiceTest` (JUnit 5 + Mockito): valida token exitoso y `BadCredentialsException`.
   - `AuthControllerTest` (MockMvc): valida escenarios E1 (200), E2 (401), E3 (403 token expirado) y 400 (campos vacíos).
