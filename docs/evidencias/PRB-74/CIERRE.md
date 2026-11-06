# Cierre de Tarea — PRB-74 (H4 — Modificadores estructurados de platos)

## 1. Resumen Ejecutivo
Se implementó el soporte de modificadores estructurados por ítem de pedido (`ModificadorItemDto`), permitiendo registrar especificaciones culinarias ("Sin cebolla", "Extra limón", "Salsa tártara aparte", etc.) con precio adicional opcional (`precio_adicional`), mostrándolos tanto en las comandas por estación de cocina (KDS) como en la pre-cuenta consolidada de la mesa.

## 2. Componentes Implementados
- **`ModificadorItemDto`**:
  - Define `nombre` y `precio_adicional` (por defecto `0.00`).
- **`ItemPedidoRequest` / `DetallePedidoResponse` / `ItemCuentaDto`**:
  - Aceptan y exponen la lista estructurada `modificadores`.
- **`PedidoService` y `CobroService`**:
  - Cuando los modificadores tienen `precio_adicional = 0.00`, mantienen intacto el precio unitario del plato; si un modificador tiene `precio_adicional > 0.00`, se adiciona al precio unitario del ítem y se recalcula el subtotal, IGV y total D13.
  - Los modificadores se persisten en `detalle_pedido.notas` con formato estructurado `[Modificadores: ...]` (100% compatible con `ddl-auto: validate`) y se exponen en `items`, `items_por_estacion` (KDS) y en `GET /api/v1/mesas/{id}/cuenta` (pre-cuenta).

## 3. Pruebas Automatizadas y Cobertura
- Se añadieron **2 pruebas de integración** en `ModificadoresPedidoIntegrationTest`:
  1. `pedidoCon2PlatosY2ModificadoresSeGuardaYMuestraEnKdsYPreCuenta` (valida el criterio exacto de un pedido con 2 platos y 2 modificadores cada uno en KDS y pre-cuenta).
  2. `modificadorConPrecioConfiguradoIncrementaTotal` (valida cálculo de recargo cuando el modificador tiene precio configurado).
- **Total suite ejecutada:** 85 tests en verde (0 fallos, 0 errores).
- **Cobertura JaCoCo:** 81.36% de líneas / 82.98% de instrucciones.
- **Checkstyle / SpotBugs:** 0 violaciones / 0 bugs.
