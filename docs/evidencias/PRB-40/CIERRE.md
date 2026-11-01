# Cierre de Tarea — PRB-40 (HU-02: Máquina de estados de mesa con bloqueo optimista y respuesta 409)

- **Rama:** `feature/PRB-40-maquina-estados-mesas`
- **Base:** `develop`
- **Responsable:** MP (Max Muñoz)
- **Ticket Jira:** PRB-40

---

## 1. Problema Resuelto (Observación de Auditoría P0)
Previamente, el endpoint `PATCH /api/v1/mesas/{id}/estado` realizaba `mesa.setEstado(nuevoEstado)` sin validar el ciclo de vida de la mesa ni proteger contra concurrencia. Si dos mozos intentaban abrir la misma mesa libre simultáneamente desde dos tablets, ambas avanzaban a comanda sin que el backend señalara conflicto HTTP 409.

---

## 2. Solución Técnica Implementada

### A. Máquina de Estados Formal de Mesa
Se formalizó el ciclo de vida de `EstadoMesa` en `MesaService.esTransicionValida`:
- `LIBRE` → `ATENDIDA` (mozo abre comanda), `OCUPADA` (pedido directo).
- `ATENDIDA` → `LIBRE` (cancelación o liberación segura PRB-39 / D11), `OCUPADA` (confirmación de comanda al backend).
- `OCUPADA` → `CUENTA_SOLICITADA` (cliente pide la cuenta), `LIBRE` (liberación administrativa).
- `CUENTA_SOLICITADA` → `LIBRE` (cobro y liberación de mesa), `OCUPADA` (cliente reanuda pedidos).

### B. Control de Errores y Respuesta HTTP 409 Conflict
Ante cualquier violación del ciclo de vida se arroja `ConflictoOperacionException` capturada por `GlobalExceptionHandler` con HTTP 409:
- Mesa ya no disponible (intento de abrir mesa que ya está `OCUPADA` o `ATENDIDA`).
- Transición no permitida (ej. de `LIBRE` a `CUENTA_SOLICITADA`).
- Petición repetida al mismo estado (`La mesa X ya se encuentra en estado Y`).

### C. Bloqueo Optimista Atómico (D7 DDL Ownership)
Dado que el esquema PostgreSQL no cuenta con columna `version` y producción utiliza `ddl-auto: validate` (D7), se implementó la concurrencia optimista a nivel de estado mediante actualización condicional atómica en `MesaRepository`:
```sql
UPDATE mesa SET estado = :nuevoEstado WHERE id = :id AND estado = :estadoEsperado
```
Si dos transacciones intentan actualizar concurrentemente, solo una actualiza 1 fila; la segunda obtiene 0 filas afectadas y arroja `ConflictoOperacionException` (HTTP 409).

---

## 3. Pruebas Automatizadas
- **`MesaServiceTest`:**
  - `transicionEstadosLibreOcupadaCuentaSolicitada`: flujo completo de estados.
  - `transicionLibreAtendidaLibre`: flujo de apertura y liberación de comanda (PRB-39).
  - `transicionInvalidaLanzaConflicto`: rechazo con 409 ante saltos de estado no permitidos.
  - `intentarAbrirMesaOcupadaLanzaConflicto`: colisión de mozos al abrir mesa ocupada.
  - `mismoEstadoLanzaConflicto`: detección de estado ya establecido.
  - `carreraConcurrenciaOptimistaLanzaConflicto`: simulación de carrera de actualización condicional.
- **`MesaControllerTest`:**
  - Verificación HTTP con MockMvc de respuestas 200, 400, 401, 403, 404 y 409.
- **Resultado de la Suite:**
  - **39 pruebas unitarias y de integración en verde (0 fallos, BUILD SUCCESS)**.
