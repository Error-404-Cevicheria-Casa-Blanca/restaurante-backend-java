# Evidencia de Cierre — PRB-89: Aceptar descuento en POST /cobrar y recalcular total/IGV con auditoría

- **Issue Jira:** `PRB-89` (Subtarea de `PRB-80`)
- **Responsable:** Max Erick Muñoz Paredes
- **Rama:** `feature/PRB-87-89-fix-estado-item-y-evidencias` → `develop`
- **Estado:** COMPLETADO Y VERIFICADO

---

## 1. Resumen Técnico

1. **DTOs de Cobro con Descuento (`CobrarRequest.java` y `CobroResponse.java`):**
   - `CobrarRequest` acepta `descuento` (monto fijo en S/), `tipo_descuento` (`"MONTO"` o `"PORCENTAJE"`) y `porcentaje_descuento` (`0` a `100`).
   - `CobroResponse` expone `total` (con descuento aplicado), `subtotal` (base imponible sin IGV redondeada a 2 decimales), `igv` (18%), `descuento` aplicado, `pagado`, `vuelto` y `pedidos`.
2. **Lógica de Negocio y Auditoría (`CobroService.java` y `PedidoService.java`):**
   - Aplica tanto en `POST /api/v1/mesas/{id}/cobrar` como en `POST /api/v1/pedidos/{id}/cobrar`.
   - Rechaza con `400 Bad Request` descuentos negativos, porcentajes mayores a `100%` o montos de descuento superiores al total de consumo.
   - Valida que la suma de pagos cubra `totalConDescuento + propina` (`400 Bad Request` si es insuficiente).
   - Registra en `evento_pedido` la auditoría del cobro indicando el descuento aplicado y el usuario ejecutor.

---

## 2. Archivos de Evidencia Cruda Adjuntos

- `docs/evidencias/PRB-89/01-tests.txt`: Salida cruda de `.\mvnw.cmd test` (`117` tests ejecutados, `0` fallos, incluyendo `MesaCobroIntegrationTest`).
- `docs/evidencias/PRB-89/02-git.txt`: Salida cruda de `git log --oneline -5`.
