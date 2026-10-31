# Cierre de Tarea — PE-12 / PRB-20 (HU-04: API REST de Pedidos y Catálogo de Productos)

- **Rama:** `feature/PE-12-api-pedidos`
- **Paquete base:** `com.lacasablanca.backend`
- **Pull Request:** #5 (contra `develop`)

## Resumen de Resolución de Observaciones de Auditoría (Revisión David)

### 1. IGV y Desglose Informativo (RF-11 / Decisión D13)
- **Opción adoptada:** Opción B (propuesta D13). Los precios de la carta incluyen IGV (18%).
- **Cálculo en backend:**
  - `total` = suma de items en carta (ej. 2 × 48.00 = 96.00).
  - `subtotal = total / 1.18` con escala 2 y `RoundingMode.HALF_UP` (96.00 / 1.18 = 81.36).
  - `igv = total - subtotal` (96.00 - 81.36 = 14.64).
- **Contrato:** Se agregaron `subtotal` e `igv` a `PedidoResponse` y se actualizó `docs/contratos/API-v1.md`.
- **Persistencia:** No se alteró `db/schema.sql`, evitando riesgos con `ddl-auto: validate`.
- **Test:** Caso unitario 2 × 48.00 verificado en `PedidoServiceTest` (subtotal 81.36, igv 14.64, total 96.00).

### 2. Prueba Real de Rollback Transaccional en Inserción de Detalle (E3)
- `PedidoRollbackTest` reescrito con `@SpringBootTest` y `@ActiveProfiles("test")`, sin `@Transactional` en la clase de test.
- El fallo se induce mediante `notas` con longitud de 300 caracteres (excede `VARCHAR(255)` de `detalle_pedido`), ocurriendo estrictamente en la inserción JPA del segundo detalle (`DataIntegrityViolationException`), **después** de que la mesa fue marcada como `OCUPADA`.
- **Prueba de mutación superada:**
  - Al comentar `@Transactional` en `PedidoService.registrarPedido`, el test falla (`expected: <LIBRE> but was: <OCUPADA>`).
  - Al restaurar `@Transactional`, el test pasa exitosamente revirtiendo la mesa a `LIBRE` y dejando el conteo de pedidos en 0.
- Evidencias en `docs/evidencias/PE-12/05-rollback-mutacion.txt`.

### 3. Cobertura Unitaria de Producto Inexistente (404)
- Se restauró el test unitario `crearPedidoProductoInexistenteLanzaNotFound` en `PedidoServiceTest.java`.
- Verifica que ante un producto con ID inexistente se lanza `RecursoNoEncontradoException` y `verifyNoInteractions(pedidoRepository)`.

### 4. División por Estaciones de Cocina (RF-08)
- Creado enum `Estacion`: `BARRA_FRIA`, `COCINA_CALIENTE`, `BEBIDAS`, `POSTRES`.
- Mapeado en `Producto` a partir de su categoría sin requerir cambios DDL en PostgreSQL.
- Expuesto en `ProductoDto`, `DetallePedidoResponse`, y agrupado en `PedidoResponse.items_por_estacion`.
- Documentado en el contrato `docs/contratos/API-v1.md`.

### 5. Seguridad y Asignación de Mozo
- Eliminado el fallback silencioso (`findAll().stream().findFirst()`).
- Si el ID del token JWT no corresponde a un usuario existente, se lanza `RecursoNoEncontradoException`.
- Test unitario con subject `"9999"` agregado en `PedidoServiceTest` verificando que no se interactúa con `pedidoRepository`.

### 6. Restauración de Mensajes de PRB-17
- Se restauraron los mensajes literales en `GlobalExceptionHandler`:
  - `AccessDeniedException`: `"Acceso denegado: solo MOZO o ADMIN pueden realizar esta acción"`
  - `HttpMessageNotReadableException`: `"Estado de mesa inválido. Valores permitidos: LIBRE, OCUPADA, ATENDIDA, CUENTA_SOLICITADA"`

### 7. Suite de Pruebas Automatizadas
- Aislamiento en tests (`@BeforeEach` en `PedidoControllerTest` limpiando pedidos y liberando mesas).
- Tests HTTP para 401 sin token en `/categorias` y `/productos`.
- Tests HTTP para 409 (mesa ocupada) y 404 (producto no encontrado).
- **Resultado final:** **30/30 pruebas en verde (BUILD SUCCESS)**.
