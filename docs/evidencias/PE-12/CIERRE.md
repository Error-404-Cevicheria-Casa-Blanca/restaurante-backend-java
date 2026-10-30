# Cierre de Tarea — PE-12 / PRB-20 (HU-04: API REST de Pedidos y Catálogo de Productos)

- **Rama:** `feature/PE-12-api-pedidos`
- **Paquete base:** `com.lacasablanca.backend`

## Cumplimiento del Contrato (`API-v1.md`) y Reglas Técnicas
1. **Endpoint `POST /api/v1/pedidos` (HTTP 201 Created):**
   - Recibe `mesa_id` e `items` con `{producto_id, cantidad, notas}`.
   - El precio unitario se copia directamente de la entidad `Producto` en base de datos (seguridad contra manipulación de precios desde el cliente).
   - Cálculo automático de subtotal y total mediante `BigDecimal` (`escala 2, RoundingMode.HALF_UP`).
   - Estado inicial del pedido: `PENDIENTE`.
   - Transaccionalidad `@Transactional`: La mesa asociada pasa atómicamente a `OCUPADA` en la misma transacción.
2. **Validaciones y Control de Errores (Bean Validation + `@RestControllerAdvice`):**
   - `400 Bad Request` ante lista de items vacía o cantidad menor a 1 con cuerpo `{"mensaje": "..."}`.
   - `409 Conflict` si la mesa ya está en estado `OCUPADA` o `CUENTA_SOLICITADA`.
   - `409 Conflict` si algún producto solicitado tiene `disponible = false`.
   - `404 Not Found` si el ID de mesa o de producto no existe.
3. **Catálogo de Productos y Categorías (D6):**
   - `GET /api/v1/categorias`: Retorna las 4 categorías oficiales (Entradas, Platos de Fondo, Bebidas, Postres).
   - `GET /api/v1/productos`: Retorna la carta con precios (`NUMERIC(10,2)`), estado `disponible`, `categoria_id` y `imagen_url`. Soporta filtrado opcional `?categoria_id=`.
4. **Pruebas Automatizadas:**
   - 25 pruebas unitarias y de integración JUnit 5 + Mockito + MockMvc en verde (**BUILD SUCCESS**).
   - Cobertura de cálculos, validaciones, disponibilidad y control de transacciones.
