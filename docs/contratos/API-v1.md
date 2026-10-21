# Contrato REST v1 (Spring Boot ↔ App Kotlin)

Leyenda: **[CONFIRMADO]** = escrito en una historia de Jira / verificado en código. **[PROPUESTA]** = el equipo aún no lo definió; usar con una marca de supuesto y confirmar con el dueño del módulo.

## Convenciones
- Base URL: emulador Android `http://10.0.2.2:8080/` · **tablets reales** `http://<IP-LAN-de-la-PC-central>:8080/` (el servidor corre en la PC del local) **[PROPUESTA D15]**; configurable, no fija en el código. HTTP en claro solo en `debug` (network security config), nunca en `release`.
- Autenticación: header `Authorization: Bearer <jwt>` en todo salvo `/auth/login` **[CONFIRMADO: JWT]**.
- JSON en snake_case **[CONFIRMADO D5]**.
- Errores: cuerpo `{"mensaje": "texto legible"}` **[CONFIRMADO]**.

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
- `estado` **[CONFIRMADO D2]**: `LIBRE | OCUPADA | ATENDIDA | CUENTA_SOLICITADA` (colores por RF-05: verde, rojo, azul, amarillo).
- 401 sin token **[CONFIRMADO]**.

## PATCH /api/v1/mesas/{id}/estado  (HU-02)
Request **[CONFIRMADO]**: `{"estado":"OCUPADA"}` → 200 **[CONFIRMADO]** con la mesa actualizada.
- Roles permitidos: `MOZO` y `ADMIN` **[CONFIRMADO]** (otros roles reciben 403).
- 400 estado inválido · 401 sin token · 404 mesa inexistente **[CONFIRMADO]**.

## GET /api/v1/categorias · GET /api/v1/productos  (**sin historia: D6**) **[PROPUESTA]**
```json
{"id":10,"nombre":"Ceviche Clásico","descripcion":"...","precio":48.00,"disponible":true,"categoria_id":2,"imagen_url":"https://.../x.jpg"}
```
Categorías según PE-8: Entradas, Platos de Fondo, Bebidas, Postres. `disponible=false` → la app lo muestra deshabilitado (HU-06, escenario 2 **[CONFIRMADO]**).

## POST /api/v1/pedidos  (HU-04)
Request **[CONFIRMADO en forma, nombres de lista PROPUESTA]**:
```json
{"mesa_id":7,"items":[{"producto_id":10,"cantidad":2,"notas":"sin picante"}]}
```
- 201 **[CONFIRMADO]**: id del pedido, detalle consolidado y total calculado por el backend; estado `PENDIENTE`.
- 400 **[CONFIRMADO]**: lista vacía o cantidad < 1, mensajes de Bean Validation.
- Fallo en un detalle → rollback completo **[CONFIRMADO]**.
- Mesa no libre → 409 **[PROPUESTA]**.
