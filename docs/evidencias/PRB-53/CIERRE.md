# Cierre de Tarea — PRB-53 ([P-06] Idempotencia, consulta y anulación de pedidos)

## 1. Resumen Ejecutivo
Se implementó el soporte de idempotencia mediante `client_request_id` (en body JSON y cabecera `Idempotency-Key`) en la creación de pedidos, consulta filtrada de pedidos e historial de auditoría (`eventos`), transición de estado auditada y anulación total o por ítem con motivo obligatorio y liberación automática de mesa si no quedan pedidos activos.

## 2. Endpoints Implementados y Actualizados
- **`POST /api/v1/pedidos`**:
  - Acepta `client_request_id` opcional en el cuerpo de la petición o cabecera `Idempotency-Key`.
  - Primera ejecución retorna `201 Created` (`replay = false`).
  - Reintentos con el mismo `client_request_id` retornan el pedido existente con `200 OK` (`replay = true`), evitando duplicados por reconexión de red en tablet Android.
- **`GET /api/v1/pedidos?mesa_id=&estado=&desde=&hasta=`**:
  - Consulta filtrada de pedidos con soporte para filtros combinados por mesa, estado y rango de fechas ISO-8601.
- **`GET /api/v1/pedidos/{id}`**:
  - Obtiene el detalle completo del pedido incluyendo sus ítems, `client_request_id`, `motivo_anulacion` y trazabilidad de `eventos`.
- **`PATCH /api/v1/pedidos/{id}/estado`**:
  - Actualiza el estado del pedido (`PENDIENTE`, `EN_PREPARACION`, `LISTO`, `SERVIDO`, `COBRADO`, `ANULADO`) registrando el evento de auditoría con usuario, timestamp y motivo.
- **`POST /api/v1/pedidos/{id}/anular`**:
  - Exige `motivo` obligatorio (`400 Bad Request` si se omite o está vacío).
  - Rechaza con `409 Conflict` si el pedido ya fue `COBRADO` o `ANULADO`.
  - Marca el pedido como `ANULADO`, registra el evento de auditoría y verifica si la mesa tiene otros pedidos activos; si no quedan pedidos activos, libera automáticamente la mesa a estado `LIBRE`.
- **`POST /api/v1/pedidos/{id}/items/{itemId}/anular`**:
  - Permite anular un ítem específico del pedido con `motivo` obligatorio, recalcula totales según D13 (precios incluyen IGV 18%) y, si el pedido queda sin ítems, anula el pedido y libera la mesa de corresponder.

## 3. Pruebas Automatizadas y Cobertura
- Se añadieron **7 pruebas de integración** en `PedidoIdempotenciaAnulacionIntegrationTest`:
  1. `crearPedidoConClientRequestIdEsIdempotenteYNoDuplica`: valida `201 Created` inicial y `200 OK` (`replay=true`) en reintentos.
  2. `crearPedidoConIdempotencyKeyHeaderEsIdempotente`: valida soporte por cabecera HTTP `Idempotency-Key`.
  3. `consultarPedidosPorMesaYEstadoYDetallePorId`: valida filtros de búsqueda y detalle con eventos.
  4. `cambiarEstadoDePedidoRegistraAuditoria`: valida `PATCH /api/v1/pedidos/{id}/estado` y registro en historial de eventos.
  5. `anularPedidoSinMotivoRetorna400`: valida obligatoriedad del motivo de anulación.
  6. `anularUnicoPedidoActivoLiberaLaMesaAutomaticamente`: valida anulación y liberación automática de mesa a `LIBRE`.
  7. `anularItemDePedidoRecalculaTotalesD13`: valida anulación parcial de ítem y recálculo de subtotal/IGV/total.
- **Total suite ejecutada:** 71 tests en verde (0 fallos, 0 errores).
- **Cobertura JaCoCo:** 83.90% de líneas / 84.05% de instrucciones.
- **Checkstyle / SpotBugs:** 0 violaciones / 0 bugs.
