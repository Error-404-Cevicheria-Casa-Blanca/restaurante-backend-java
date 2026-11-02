# Cierre de Tarea — PRB-40 (HU-02: Máquina de estados de mesa con bloqueo optimista y respuesta 409)

- **Rama:** `feature/PRB-40-maquina-estados-mesas`
- **Base:** `develop`
- **Responsable:** MP (Max Muñoz)
- **Ticket Jira:** PRB-40

---

## 1. Problema Resuelto (Observaciones de Revisión PRB-40)
Previamente, el endpoint `PATCH /api/v1/mesas/{id}/estado` no gestionaba mozo responsable ni marcas de tiempo, y `PedidoService` realizaba modificaciones directas al estado de la mesa sin pasar por la máquina de estados ni el control de concurrencia optimista.

---

## 2. Solución Técnica Implementada

### A. Mozo Responsable y Marcas de Tiempo de Apertura y Cierre
1. **Esquema (`db/schema.sql` - Django / D7):**
   Se agregaron a la tabla `mesa`:
   - `mozo_id INT REFERENCES usuario(id) ON DELETE SET NULL`
   - `fecha_apertura TIMESTAMP`
   - `fecha_cierre TIMESTAMP`
2. **Modelo JPA (`Mesa.java`):**
   - `@ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "mozo_id") private Usuario mozo;`
   - `@Column(name = "fecha_apertura") private LocalDateTime fechaApertura;`
   - `@Column(name = "fecha_cierre") private LocalDateTime fechaCierre;`
   - *Nota:* Se mantuvo `MesaDto` inalterado para preservar la compatibilidad con el contrato de la app Android.
3. **Lógica de Servicio (`MesaService.java`):**
   - Nueva firma: `actualizarEstado(Integer id, EstadoMesa nuevoEstado, String identificadorUsuario)`.
   - Al abrir desde `LIBRE` a `ATENDIDA` u `OCUPADA`: se resuelve el mozo (soporta id numérico o username vía JWT subject) y se marca `fechaApertura = now()` y `fechaCierre = null`.
   - Al liberar a `LIBRE`: se marca `fechaCierre = now()`.

### B. Integración Estricta con `PedidoService`
- Se inyectó `MesaService` en `PedidoService`, reemplazando el `save` directo por la llamada `mesaService.actualizarEstado(mesa.getId(), EstadoMesa.OCUPADA, username)`.
- Se conserva la regla de negocio que rechaza pedidos si la mesa está `CUENTA_SOLICITADA`.
- La propagación transaccional une ambas operaciones: si el pedido falla, la transición de mesa revierte limpiamente (`PedidoRollbackTest` en verde).

### C. Máquina de Estados e Idempotencia
- Whitelist cerrada en `MesaService.esTransicionValida`:
  - `LIBRE` → `ATENDIDA`, `OCUPADA`
  - `ATENDIDA` → `LIBRE`, `OCUPADA`
  - `OCUPADA` → `CUENTA_SOLICITADA`, `LIBRE`
  - `CUENTA_SOLICITADA` → `LIBRE`, `OCUPADA`
- **Idempotencia:** `LIBRE` → `LIBRE` y `CUENTA_SOLICITADA` → `CUENTA_SOLICITADA` responden 200 OK sin error.
- **Detección de Conflicto 409:** Intentos de repetir estados activos (`ATENDIDA` → `ATENDIDA`, `OCUPADA` → `OCUPADA`) o transiciones no permitidas arrojan `ConflictoOperacionException` (HTTP 409) consultando el estado fresco en la base de datos con `findEstadoById`.

### D. Bloqueo Optimista Atómico (D7 DDL Ownership)
- Conforme a la regla D7 (`ddl-auto: validate` en producción y esquema owned por Django), no se añadió columna `version` artificial.
- Se implementó actualización condicional atómica en `MesaRepository`:
  ```sql
  UPDATE mesa SET estado = :nuevoEstado WHERE id = :id AND estado = :estadoEsperado
  ```
- Se añadió prueba multihilo con 2 hilos reales (`MesaConcurrenciaTest`) utilizando `CountDownLatch`, validando que ante colisión simultánea exactamente 1 gana (200 OK) y 1 recibe 409 Conflict.

---

## 3. Pruebas Automatizadas
- **`MesaServiceTest` (8 pruebas unitarias):** Transiciones válidas, idempotencia, colisiones concurrentes, apertura/cierre con mozo y fechas.
- **`MesaControllerTest` (11 pruebas de integración MockMvc):** Códigos HTTP 200, 400, 401, 403, 404 y 409, y persistencia de mozo.
- **`MesaConcurrenciaTest` (1 prueba multihilo real):** Concurrencia real de 2 hilos compitiendo por abrir la misma mesa.
- **`PedidoServiceTest` & `PedidoRollbackTest`:** Verificación de invocación a `MesaService` y rollback transaccional ante fallos.
- **Total de la Suite:** **42 pruebas en verde (0 fallos, 0 errores, BUILD SUCCESS)**.

---

## 4. Evidencias Verificadas
- `docs/evidencias/PRB-40/01-tests.txt`: Salida oficial de `mvnw clean test` (42 tests).
- `docs/evidencias/PRB-40/02-curl.txt`: Transcripción de ejecución real con `curl.exe` cubriendo la secuencia estricta de 12 pasos.
- `docs/evidencias/PRB-40/03-git.txt`: Estadísticas de cambios frente a `origin/develop...HEAD`.
