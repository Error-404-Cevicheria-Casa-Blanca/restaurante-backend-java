# Evidencia de Cierre — PRB-73: H1 — UI de estado de impresión y reimpresión

- **Issue Jira:** `PRB-73`
- **Responsable:** Max Erick Muñoz Paredes
- **Rama:** `feature/PRB-69-go-live-y-cierre`
- **Estado:** COMPLETADO Y VERIFICADO

---

## 1. Resumen de Implementación (Cumplimiento ADR-02)

1. **DTO de Trabajo de Impresión (`TrabajoImpresionDto.java`) y Extensión de `CuentaResponse.java`:**
   - Expone `job_id`, `mesa_id`, `tipo` (`PRECUENTA_80MM`), `estado` (`EN_COLA`, `IMPRESO`, `ERROR`), `intentos`, `mensaje_error` y `fecha_solicitud`.
   - `POST /api/v1/mesas/{id}/precuenta` y `GET /api/v1/mesas/{id}/cuenta` incluyen `impresion_job_id`, `estado_impresion` e `intentos_impresion`.
2. **Endpoints de Consulta de Estado y Reimpresión (`MesaController.java` / `CobroService.java`):**
   - `GET /api/v1/mesas/{id}/impresion`: permite a la tablet mostrar en tiempo real el indicador de estado del trabajo de impresión (`EN_COLA`, `IMPRESO`, `ERROR`).
   - `POST /api/v1/mesas/{id}/precuenta/reimprimir`: solicita un nuevo trabajo de impresión al backend central (sin abrir conexiones directas desde Android, cumpliendo **ADR-02**), incrementando el contador de intentos y reportando `ERROR` limpio si la impresora está apagada sin bloquear la comanda ni el cobro.
3. **Pruebas de Integración (`ImpresionPrecuentaIntegrationTest.java`):**
   - Valida solicitud inicial de pre-cuenta (`IMPRESO`, intento `1`), consulta de estado (`GET /api/v1/mesas/7/impresion`), simulación de impresora apagada (`POST /api/v1/mesas/7/precuenta/reimprimir?simular_error=true` -> `ERROR`, intento `2`), reintento exitoso (`IMPRESO`, intento `3`) y cobro normal de la mesa.
