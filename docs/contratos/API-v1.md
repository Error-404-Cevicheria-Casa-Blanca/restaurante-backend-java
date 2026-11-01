# Contrato REST v1 (Spring Boot ↔ App Kotlin)

Leyenda: **[CONFIRMADO]** = escrito en una historia de Jira / verificado en código. **[PROPUESTA]** = el equipo aún no lo definió; usar con una marca de supuesto y confirmar con el dueño del módulo.

## Convenciones
- Base URL: emulador Android `http://10.0.2.2:8080/` · **tablets reales** `http://<IP-LAN-de-la-PC-central>:8080/` (el servidor corre en la PC del local) **[PROPUESTA D15]**; configurable, no fija en el código. HTTP en claro solo en `debug` (network security config), nunca en `release`.
- Autenticación: header `Authorization: Bearer <jwt>` en todo salvo `/auth/login` **[CONFIRMADO: JWT]**.
- JSON en snake_case **[CONFIRMADO D5]**.
- Errores: cuerpo `{"mensaje": "texto legible"}` **[CONFIRMADO]**.
- Precios e IGV (D13): Los precios en carta **incluyen IGV (18%)**. El backend expone desglose informativo: `subtotal = total / 1.18` e `igv = total - subtotal` calculados con `BigDecimal`, escala 2 y `HALF_UP` **[CONFIRMADO D13]**.

## POST /api/v1/auth/login  (HU-01)
Request **[CONFIRMADO D4]**: `{"usuario": "string", "clave": "string"}`
- 200 **[CONFIRMADO]**: JWT que contiene id de usuario (`sub`) y rol (`rol`). Cuerpo **[CONFIRMADO D4]**: `{"token":"...","tipo":"Bearer","usuario":{"id":1,"nombre":"...","rol":"MOZO"}}`
- 401 **[CONFIRMADO]**: credenciales inválidas, mensaje claro y **genérico** (`{"mensaje": "Usuario o contraseña incorrectos"}`).
- Token expirado en endpoint protegido → 403 **[CONFIRMADO, ver D9]**.

## GET /api/v1/mesas  (HU-02)
- 200 **[CONFIRMADO]**: lista JSON con `id`, `numero`, `capacidad`, `ubicacion`, `estado`.
```json
[{"id":1,"numero":1,"capacidad":4,"ubicacion":"Primer piso","estado":"LIBRE"}]
```
- `estado` **[CONFIRMADO D2]**: `LIBRE | OCUPADA | ATENDIDA | CUENTA_SOLICITADA`.
- 401 sin token **[CONFIRMADO]**.

## PATCH /api/v1/mesas/{id}/estado  (HU-02 / PRB-40)
Request **[CONFIRMADO]**: `{"estado":"OCUPADA"}` → 200 **[CONFIRMADO]** con la mesa actualizada.
- Roles permitidos: `MOZO` y `ADMIN` **[CONFIRMADO]**.
- **Máquina de Estados de Mesa (PRB-40)**:
  * `LIBRE` → `ATENDIDA` (mozo abre comanda), `OCUPADA` (pedido directo).
  * `ATENDIDA` → `LIBRE` (cancelación o liberación PRB-39), `OCUPADA` (confirmación de comanda).
  * `OCUPADA` → `CUENTA_SOLICITADA` (cliente pide la cuenta), `LIBRE` (liberación administrativa).
  * `CUENTA_SOLICITADA` → `LIBRE` (cobro y liberación), `OCUPADA` (cliente reanuda pedido).
- 400 estado inválido (`{"mensaje": "Estado de mesa inválido. Valores permitidos: LIBRE, OCUPADA, ATENDIDA, CUENTA_SOLICITADA"}`).
- 401 sin token.
- 404 mesa inexistente (`{"mensaje": "Mesa no encontrada con ID: X"}`).
- 409 Conflicto **[CONFIRMADO PRB-40]**:
  * Mesa no disponible (ya tomada por otro mozo): `{"mensaje": "La mesa X no está disponible. Estado actual: Y"}`.
  * Transición no permitida por máquina de estados: `{"mensaje": "Transición de estado no permitida: de X a Y para la mesa Z"}`.
  * Reintento de mismo estado: `{"mensaje": "La mesa X ya se encuentra en estado Y"}`.
  * Concurrencia optimista atómica (D7 DDL Ownership): `{"mensaje": "Conflicto de concurrencia: la mesa X cambió a estado Y por otra operación"}`.

## GET /api/v1/categorias · GET /api/v1/productos  (D6) **[CONFIRMADO]**
- Protegidos con token JWT (401 sin token) **[CONFIRMADO]**.
- `GET /api/v1/categorias`: `[{"id":1,"nombre":"Entradas"},{"id":2,"nombre":"Platos de Fondo"},...]`
- `GET /api/v1/productos` (soporta filtro opcional `?categoria_id=`):
```json
[
  {
    "id": 1,
    "nombre": "Ceviche Clásico",
    "descripcion": "Pescado fresco marinado en limón",
    "precio": 48.00,
    "disponible": true,
    "categoria_id": 2,
    "imagen_url": "https://lacasablanca.pe/ceviche.jpg",
    "estacion": "BARRA_FRIA"
  }
]
```
Estaciones operativas (RF-08): `BARRA_FRIA`, `COCINA_CALIENTE`, `BEBIDAS`, `POSTRES` **[CONFIRMADO]**.

## POST /api/v1/pedidos  (HU-04 / PRB-20) **[CONFIRMADO]**
Request:
```json
{"mesa_id":7,"items":[{"producto_id":1,"cantidad":2,"notas":"sin picante"}]}
```
- 201 **[CONFIRMADO]**: Registro transaccional `@Transactional`. La mesa pasa automáticamente a `OCUPADA`. Precio unitario copiado de la base de datos con cálculo `BigDecimal` (escala 2, `HALF_UP`).
Respuesta:
```json
{
  "id": 1,
  "mesa_id": 7,
  "mozo_id": 2,
  "mozo_nombre": "mozo_01",
  "estado": "PENDIENTE",
  "subtotal": 81.36,
  "igv": 14.64,
  "total": 96.00,
  "fecha_creacion": "2026-10-30T01:18:17",
  "items": [
    {
      "id": 1,
      "producto_id": 1,
      "producto_nombre": "Ceviche Clásico",
      "estacion": "BARRA_FRIA",
      "precio_unitario": 48.00,
      "cantidad": 2,
      "notas": "sin picante",
      "subtotal": 96.00
    }
  ],
  "items_por_estacion": {
    "BARRA_FRIA": [ ... ]
  }
}
```
- 400 **[CONFIRMADO]**: Lista de items vacía o cantidad < 1.
- 404 **[CONFIRMADO]**: Mesa, producto o mozo no encontrado.
- 409 **[CONFIRMADO]**: Mesa no disponible (ya está `OCUPADA` o `CUENTA_SOLICITADA`) o producto no disponible (`disponible=false`).
- Fallo en un detalle → Rollback completo `@Transactional` (la mesa permanece en `LIBRE` y no se persiste el pedido) **[CONFIRMADO]**.
