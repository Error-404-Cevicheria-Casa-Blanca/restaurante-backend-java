# Acta de Cierre Técnico · PRB-85

**Tarea:** `PRB-85`: Backend Java: ampliar Login y /auth/me con nombre visible, turno y terminal  
**Responsable:** Max Erick Muñoz Paredes  
**Rama:** `feature/PRB-85-login-turno-terminal`  
**Base:** `develop` (commit `41a205c`)  
**Fecha:** 2026-11-10  

---

## 1. Resumen de Implementación

1. **Modelos JPA y Repositorios:**
   - Entidad `Turno` (`turno`): mapeo de turnos de salón (`id`, `nombre`, `hora_inicio`, `hora_fin`, `activo`).
   - Entidad `Terminal` (`terminal`): mapeo de tablets POS (`id`, `codigo`, `nombre`, `activo`).
   - Entidad `SesionMozo` (`sesion_mozo`): auditoría operativa con relación `@ManyToOne` hacia `usuario`, `terminal` y `turno`, con marcas de tiempo `inicio` y `fin`.
   - Repositorios: `TurnoRepository`, `TerminalRepository` y `SesionMozoRepository`.

2. **DTOs y Contratos REST:**
   - `TurnoDto`: campos `id`, `nombre`, `inicio`, `fin`.
   - `TerminalDto`: campos `id`, `codigo`, `nombre`.
   - `UsuarioDto`: enriquecido con `nombre_visible`, `turno` y `terminal`, preservando compatibilidad estricta con campos existentes (`id`, `nombre`, `rol`).
   - `LoginResponse`: enriquecido con `fecha_hora_servidor` en formato ISO-8601 con zona horaria `America/Lima`.
   - `LoginRequest` y `PinLoginRequest`: soporte opcional de `terminal_codigo`.

3. **Lógica de Servicio (`AuthService`):**
   - Resolución automática de `Turno` activo según la hora actual en `America/Lima`.
   - Validación de `terminal_codigo` contra la base de datos (rechaza terminales no registradas o inactivas con HTTP 400).
   - Registro de sesión en `sesion_mozo` (cierra sesiones previas activas y abre la nueva).
   - Generación de `nombre_visible` profesional legible.
   - Endpoint `/api/v1/auth/me` devuelve el perfil completo (`usuario_id`, `roles`, `usuario`, `turno`, `terminal`, `fecha_hora_servidor`).

4. **Documentación de Contratos:**
   - Actualizado `docs/contratos/API-v1.md`.
   - Actualizado `docs/produccion/CONTRATO-API-V2.md`.

---

## 2. Resultados de Calidad y Pruebas

- **Checkstyle:** 0 violations.
- **SpotBugs:** 0 bugs / 0 warnings (`BugInstance size is 0`).
- **Pruebas Automatizadas:** 103/103 tests passing (`BUILD SUCCESS`).
  - `AuthControllerTest`: login con terminal, login sin terminal, terminal inválida, `/auth/me` con y sin token.
  - `AuthServiceTest`: lógica unitaria con mocks para turnos y terminales.
  - `AuthRolesPinIntegrationTest`: regresión y matriz de permisos intacta.
