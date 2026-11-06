# Cierre de Tarea — PRB-75 (H5 — Gestión de mesas: división, fusión y transferencia)

## 1. Resumen Ejecutivo
Se implementaron las operaciones transaccionales de salón para dividir cuentas (`POST /api/v1/mesas/{id}/dividir`), fusionar mesas ocupadas (`POST /api/v1/mesas/{id}/fusionar`) y transferir pedidos hacia otra mesa libre (`POST /api/v1/mesas/{id}/transferir`), garantizando cuadre exacto de importes sin duplicación de ítems, trazabilidad en auditoría y validación estricta de estados con retorno `409 Conflict` ante transiciones inválidas.

## 2. Endpoints Implementados
- **`POST /api/v1/mesas/{id}/dividir`**:
  - Recibe `item_ids` y `mesa_destino_id` opcional.
  - Extrae los ítems seleccionados del pedido original sin duplicarlos, genera un nuevo pedido independiente, recalcula ambos totales D13 (`total_origen + total_nuevo == total_combinado`) y ocupa la mesa destino si es distinta.
- **`POST /api/v1/mesas/{id}/fusionar`**:
  - Recibe `mesa_origen_id`, valida que ambas mesas tengan pedidos activos (`409 Conflict` si alguna está `LIBRE`), unifica todos los pedidos bajo la mesa destino sin duplicar ítems y libera automáticamente la mesa origen a `LIBRE`.
- **`POST /api/v1/mesas/{id}/transferir`**:
  - Recibe `mesa_destino_id`, valida que la mesa origen esté ocupada y la mesa destino esté disponible (`409 Conflict` si la mesa destino ya está `OCUPADA`), traslada los pedidos activos registrando el evento de auditoría (`Transferencia de mesa X a mesa Y`), ocupa la mesa destino y libera la mesa origen a `LIBRE`.

## 3. Pruebas Automatizadas y Cobertura
- Se añadieron **4 pruebas integrales** en `MesaDivisionFusionTransferenciaIntegrationTest`:
  1. `dividirMesaGeneraDosCuentasIndependientesYCuadradas`
  2. `fusionarDosMesasUnePedidosSinDuplicados`
  3. `transferirMesaMuevePedidoSinPerderTrazabilidad`
  4. `estadosInvalidosResponden409`
- **Total suite ejecutada:** 89 tests en verde (0 fallos, 0 errores).
- **Cobertura JaCoCo:** 81.96% de líneas / 83.17% de instrucciones.
- **Checkstyle / SpotBugs:** 0 violaciones / 0 bugs.
