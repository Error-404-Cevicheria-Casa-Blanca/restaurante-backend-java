# Cierre de Tarea — PRB-79 (H13 — Pedidos "Para Llevar" o Delivery)

## 1. Resumen Ejecutivo
Se habilitó el registro, seguimiento por estaciones en cocina (KDS) y cobro directo de pedidos sin mesa asignada (`mesa_id = null`, modalidades `PARA_LLEVAR` y `DELIVERY`), garantizando que las 13 mesas del salón permanezcan intactas y permitiendo cobrar directamente en `POST /api/v1/pedidos/{id}/cobrar` o `POST /api/v1/pedidos/{id}/pagos`.

## 2. Cambios Implementados
- **`CrearPedidoRequest` y `PedidoResponse`**:
  - `mesa_id` pasa a ser opcional (`null` para pedidos Para Llevar o Delivery).
  - Se añadieron los campos `para_llevar` (`boolean`), `tipo_pedido` (`SALON`, `PARA_LLEVAR`, `DELIVERY`) y `cliente_nombre`.
- **`Pedido.java`, `Pago.java`, `PedidoService.java` y `CobroService.java`**:
  - Soporte de `@JoinColumn(name = "mesa_id", nullable = true)` en `Pedido` y `Pago`.
  - Cuando `mesa_id == null`, `PedidoService` registra el pedido, agrupa sus ítems por estación (`items_por_estacion`) para el KDS y omite cualquier transición de estado sobre las mesas físicas del salón.
  - `CobroService.cobrarPedido` (`POST /api/v1/pedidos/{id}/cobrar` y `/pagos`) registra el pago en la tabla `pago` con `mesa = null` y marca el pedido como `COBRADO` sin alterar mesas.

## 3. Pruebas Automatizadas y Cobertura
- Se añadieron **2 pruebas de integración E2E** en `PedidoParaLlevarDeliveryIntegrationTest`:
  1. `flujoCompletoPedidoParaLlevarSinMesa` (creación sin `mesa_id`, distribución KDS, transición a `LISTO` y cobro por Yape sin tocar las 13 mesas).
  2. `pedidoDeliveryCoexisteConFlujoDeSalon` (coexistencia simultánea entre pedidos de salón y pedidos Delivery).
- **Total suite ejecutada:** 91 tests en verde (0 fallos, 0 errores).
- **Cobertura JaCoCo:** 82.41% de líneas / 83.55% de instrucciones.
- **Checkstyle / SpotBugs:** 0 violaciones / 0 bugs.
