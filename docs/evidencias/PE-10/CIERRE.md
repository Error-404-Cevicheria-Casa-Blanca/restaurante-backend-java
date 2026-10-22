# Cierre de Tarea — PE-10 (HU-02: API REST de Mesas)

- **Rama:** `feature/PE-10-api-mesas`
- **Paquete base:** `com.lacasablanca.backend`

## Cumplimiento del Contrato (`API-v1.md`) y Reglas Técnicas
1. **Enum `EstadoMesa` (D2):** valores `LIBRE`, `OCUPADA`, `ATENDIDA`, `CUENTA_SOLICITADA` usado en entidad `Mesa`, `MesaDto`, `EstadoMesaRequest` y `MesaService`.
2. **Separación Entidad ≠ DTO (`MesaDto`):** `GET /api/v1/mesas` y `PATCH /api/v1/mesas/{id}/estado` devuelven `MesaDto` en `snake_case`.
3. **Manejo global de errores (`@RestControllerAdvice` - `GlobalExceptionHandler`):**
   - `400 Bad Request` con `{"mensaje": "..."}` ante estados inválidos (ej. `"EN_ATENCION"`).
   - `404 Not Found` con `{"mensaje": "..."}` cuando el ID de mesa no existe (`RecursoNoEncontradoException`).
   - `401 Unauthorized` sin token JWT (`AuthenticationEntryPoint`).
   - `403 Forbidden` si un usuario sin rol `MOZO` o `ADMIN` (ej. `COCINERO`) intenta modificar una mesa.
4. **Pruebas unitarias y de integración (`16 tests, 0 fallos`):**
   - `MesaServiceTest` (Mockito): prueba la transición `LIBRE -> OCUPADA -> CUENTA_SOLICITADA` y error 404.
   - `MesaControllerTest` (MockMvc): prueba escenarios 200 (GET y PATCH con `ATENDIDA`/`OCUPADA`), 401 (sin token), 400 (estado inválido), 404 (mesa inexistente) y 403 (rol `COCINERO`).
5. **Contrato actualizado:** `docs/contratos/API-v1.md` actualizado con `[CONFIRMADO]`.
