# Evidencia de Cierre — PRB-68: [P-22] Pruebas de carga y caos en la LAN

- **Issue Jira:** `PRB-68`
- **Responsable:** Max Erick Muñoz Paredes
- **Rama:** `feature/PRB-65-infraestructura-y-produccion`
- **Estado:** COMPLETADO Y VERIFICADO

---

## 1. Entregables Implementados

1. **Suite de Pruebas de Carga y Caos en LAN (`scripts/loadtest/locustfile_produccion_lan.py`):**
   - Modela la operación simultánea de las tablets Android y pantallas KDS sobre las 13 mesas físicas y pedidos para llevar.
   - Incluye prueba de caos de microcortes Wi-Fi enviando cada pedido dos veces con el mismo `client_request_id` (`Idempotency-Key`) para certificar cero duplicados.
2. **Reporte de Carga y Caos (`docs/evidencias/PRB-68/01-reporte-carga-y-caos.txt`):**
   - **2,080 peticiones ejecutadas**, **0.0% de errores**, **latencia p95 agregada de 64 ms** (pico en creación de pedidos de **112 ms**, muy por debajo del umbral máximo de **300 ms**).
   - Validados los 3 escenarios de caos: microcorte Wi-Fi con reintento idempotente (`0` duplicados), colisión concurrente de 2 mozos sobre la misma mesa (`409 Conflict`), y reinicio de servicios con integridad transaccional (`@Transactional`).

---

## 2. Verificación de Criterios de Aceptación

| Criterio de Aceptación | Meta | Resultado Obtenido | Estado |
|---|---|---|---|
| Latencia p95 en LAN con tablets y KDS concurrentes | `< 300 ms` | `64 ms` (máx `112 ms` en POST) | CUMPLE |
| Pedidos duplicados ante microcortes Wi-Fi y reintentos | `0 duplicados` | `0 duplicados` (`200 OK` replay) | CUMPLE |
| Colisión de dos mozos abriendo la misma mesa | `409 Conflict` limpio | `409 Conflict` con detalle de mesa | CUMPLE |
| Integridad de BD tras reinicio de servicio | `0 registros corruptos` | `100% íntegro` (`@Transactional`) | CUMPLE |
