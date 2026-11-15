# Evidencia de Cierre — PRB-87: Endpoint PATCH /api/v1/pedidos/{id}/items/{itemId}/estado y estado por plato

- **Issue Jira:** `PRB-87` (Subtarea de `PRB-76`)
- **Responsable:** Max Erick Muñoz Paredes
- **Rama:** `feature/PRB-87-89-fix-estado-item-y-evidencias` → `develop`
- **Estado:** COMPLETADO Y VERIFICADO

---

## 1. Resumen Técnico

1. **Enum canónico `EstadoItem` compatible con BD y KDS Django (`src/main/java/com/lacasablanca/backend/model/EstadoItem.java`):**
   - Incluye los 6 valores canónicos de `db/schema.sql` y `catalogo/kds_service.py`: `PENDIENTE`, `COCINANDO`, `EN_PREPARACION`, `LISTO`, `SERVIDO`, `ANULADO`.
   - Mapa explícito de transiciones (`TRANSICIONES`):
     - `PENDIENTE` → `[COCINANDO, EN_PREPARACION, ANULADO]`
     - `COCINANDO` → `[EN_PREPARACION, LISTO, ANULADO]`
     - `EN_PREPARACION` → `[LISTO, ANULADO]`
     - `LISTO` → `[SERVIDO, ANULADO]`
     - `SERVIDO` y `ANULADO` → estados finales sin salida (`[]`).
2. **Endpoint `PATCH /api/v1/pedidos/{pedidoId}/items/{itemId}/estado` (`PedidoController.java` / `PedidoService.java`):**
   - Valida transiciones permitidas (`400 Bad Request` en saltos, retrocesos o repeticiones), pertenencia del ítem al pedido (`404 Not Found`), estado del pedido (`409 Conflict` si `COBRADO` o `ANULADO`) y permisos por rol (`COCINERO`, `MOZO`, `ADMIN`).
   - Registra auditoría en `evento_pedido` con motivo `Ítem {id}: {actual} → {nuevo}`.
3. **Exposición en `GET /api/v1/pedidos` y `GET /api/v1/pedidos/{id}` (`DetallePedidoResponse.java`):**
   - Cada ítem incluye el campo `estado` y lee sin excepción filas actualizadas directamente por el KDS de Django con `COCINANDO` o `ANULADO`.

---

## 2. Archivos de Evidencia Cruda Adjuntos

- `docs/evidencias/PRB-87/01-tests.txt`: Salida cruda de `.\mvnw.cmd test` (`117` tests ejecutados, `0` fallos, incluyendo `EstadoItemPedidoIntegrationTest`).
- `docs/evidencias/PRB-87/02-git.txt`: Salida cruda de `git log --oneline -5`.
