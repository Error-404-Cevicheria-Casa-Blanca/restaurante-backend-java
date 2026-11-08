# Evidencia de Cierre — PRB-82: Backend: API de comanda existente y extensión de pedidos

- **Issue Jira:** `PRB-82`
- **Responsable:** Max Erick Muñoz Paredes
- **Rama:** `feature/PRB-82-extender-comanda-existente`
- **Estado:** COMPLETADO Y VERIFICADO

---

## 1. Resumen de Implementación

1. **Consulta de Consumo Actual por Mesa (`GET /api/v1/pedidos?mesa_id=X`):**
   - Retorna la lista de pedidos activos de la mesa `X` con todas sus líneas (`items`), modificadores, desglose `subtotal`/`igv`/`total` e `items_por_estacion`.
2. **Ampliación de Comanda Existente en Mesa `OCUPADA` (`POST /api/v1/pedidos` y `POST /api/v1/pedidos/{id}/items`):**
   - Cuando la mesa está `OCUPADA` y posee una comanda activa (`estado != COBRADO && estado != ANULADO`), `POST /api/v1/pedidos` (así como el endpoint explícito `POST /api/v1/pedidos/{id}/items`) agrega los nuevos platos a la comanda existente sin duplicar el registro de `Pedido`, recalculando `total`, `subtotal`, `igv` e `items_por_estacion` y registrando el evento de auditoría `"Ampliación de comanda existente"`.
   - Mantiene intactas las validaciones de conflicto `409 Conflict` (cuando la mesa está en `CUENTA_SOLICITADA`, cuando un producto está agotado o cuando una mesa fue bloqueada sin pedido activo) y `400 Bad Request` (lista de ítems vacía o cantidad `< 1`).
3. **Pruebas de Integración (`PedidoExtensionComandaIntegrationTest.java`):**
   - Verifica `GET /api/v1/pedidos?mesa_id=4`, ampliación vía `POST /api/v1/pedidos` en mesa `OCUPADA`, ampliación vía `POST /api/v1/pedidos/{id}/items`, cero duplicados en base de datos y respuestas `400` y `409`.
