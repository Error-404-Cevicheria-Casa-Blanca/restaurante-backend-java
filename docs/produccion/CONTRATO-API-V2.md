# Contrato REST API v2 y Definición de Terminado (DoD) — PRB-49 ([P-01])

**Fecha de congelamiento:** 2026-11-05
**Estado:** APROBADO Y CONGELADO (Backend Spring Boot + Backoffice Django + App Android Kotlin)
**Base URL LAN:** `http://<IP-LAN-PC>:8080/api/v1`

---

## 1. Convenciones Generales
- **Autenticación:** Header `Authorization: Bearer <jwt>` (excepto `/auth/login`, `/auth/pin`, `/health`, `/version`).
- **Duración de sesión de turno:** 12 horas (`43200000` ms).
- **Formato JSON:** `snake_case` unificado en todos los DTOs.
- **Tributación Perú (D13):** Todos los precios de carta incluyen IGV (18%). `subtotal = round(total / 1.18, 2)`, `igv = total - subtotal`.

---

## 2. Endpoints Oficiales v2

### 2.1 Autenticación y Control de Acceso (`PRB-54`, `PRB-85`)
| Método | Ruta | Request | Response | Códigos HTTP | Roles |
|---|---|---|---|---|---|
| `POST` | `/api/v1/auth/login` | `{usuario, clave, terminal_codigo?}` | `{token, tipo, usuario:{id, nombre, nombre_visible, rol, turno, terminal}, fecha_hora_servidor}` | `200`, `400`, `401`, `423` | Público |
| `POST` | `/api/v1/auth/pin` | `{usuario, pin, terminal_codigo?}` (4 dígitos) | `{token, tipo, usuario:{id, nombre, nombre_visible, rol, turno, terminal}, fecha_hora_servidor}` | `200`, `400`, `401`, `423` | Público |
| `GET` | `/api/v1/auth/me` | — | `{usuario_id, roles, usuario, turno, terminal, fecha_hora_servidor}` | `200`, `401`, `403` | Todos |
| `POST` | `/api/v1/auth/logout` | Header `Bearer` | Sin contenido | `204`, `401` | Todos |

### 2.2 Mesas, Salón, Pre-cuenta y Cobro (`PRB-40`, `PRB-43`, `PRB-55`, `PRB-75`)
| Método | Ruta | Request | Response | Códigos HTTP | Roles |
|---|---|---|---|---|---|
| `GET` | `/api/v1/mesas` | — | Lista de las 13 mesas ordenadas | `200`, `401` | Todos |
| `PATCH` | `/api/v1/mesas/{id}/estado` | `{estado}` | `MesaDto` actualizado | `200`, `400`, `403`, `404`, `409` | `MOZO`, `ADMIN` |
| `GET` | `/api/v1/mesas/{id}/cuenta` | — | `{mesa_id, pedidos, items, subtotal, igv, total, propina_sugerida}` | `200`, `404`, `409` | `MOZO`, `CAJERO`, `ADMIN` |
| `POST` | `/api/v1/mesas/{id}/precuenta` | — | Cuenta consolidada + mesa pasa a `CUENTA_SOLICITADA` | `200`, `404`, `409` | `MOZO`, `CAJERO`, `ADMIN` |
| `POST` | `/api/v1/mesas/{id}/cobrar` | `{pagos:[{metodo,monto,referencia}], propina, observacion, client_request_id}` | `{total, pagado, vuelto, pedidos}` | `201`, `400`, `409` | `MOZO`, `CAJERO`, `ADMIN` |
| `POST` | `/api/v1/mesas/{id}/dividir` | `{item_ids, mesa_destino_id}` | `DivisionMesaResponse` | `200`, `404`, `409` | `MOZO`, `CAJERO`, `ADMIN` |
| `POST` | `/api/v1/mesas/{id}/fusionar` | `{mesa_origen_id}` | `CuentaResponse` unificada | `200`, `404`, `409` | `MOZO`, `CAJERO`, `ADMIN` |
| `POST` | `/api/v1/mesas/{id}/transferir` | `{mesa_destino_id}` | `CuentaResponse` en mesa destino | `200`, `404`, `409` | `MOZO`, `CAJERO`, `ADMIN` |

### 2.3 Pedidos, Idempotencia, Anulación, Modificadores y Para Llevar (`PRB-53`, `PRB-74`, `PRB-79`)
| Método | Ruta | Request | Response | Códigos HTTP | Roles |
|---|---|---|---|---|---|
| `POST` | `/api/v1/pedidos` | `{mesa_id, items:[{producto_id,cantidad,notas,modificadores}], client_request_id, tipo_pedido, cliente_nombre}` | `PedidoResponse` (`201` nuevo / `200` replay) | `201`, `200`, `400`, `404`, `409` | `MOZO`, `ADMIN` |
| `GET` | `/api/v1/pedidos` | Query `mesa_id`, `estado`, `desde`, `hasta` | Lista de `PedidoResponse` | `200`, `401` | Todos |
| `GET` | `/api/v1/pedidos/{id}` | — | `PedidoResponse` con `eventos` | `200`, `404` | Todos |
| `PATCH` | `/api/v1/pedidos/{id}/estado` | `{estado, motivo}` | `PedidoResponse` | `200`, `400`, `409` | `COCINERO`, `MOZO`, `CAJERO`, `ADMIN` |
| `POST` | `/api/v1/pedidos/{id}/anular` | `{motivo}` | `PedidoResponse` (libera mesa si sin activos) | `200`, `400`, `409` | `MOZO`, `ADMIN` |
| `POST` | `/api/v1/pedidos/{id}/items/{itemId}/anular` | `{motivo}` | `PedidoResponse` recalculado | `200`, `400`, `409` | `MOZO`, `ADMIN` |
| `PATCH` | `/api/v1/pedidos/{id}/items/{itemId}/estado` | `{estado}` (PRB-87) | `DetallePedidoResponse` (ítem actualizado, con `estado`) | `200`, `400`, `403`, `404`, `409` | `COCINERO`, `MOZO`, `ADMIN` |
| `POST` | `/api/v1/pedidos/{id}/cobrar` (`/pagos`) | `CobrarRequest` | `CobroResponse` | `201`, `400`, `409` | `MOZO`, `CAJERO`, `ADMIN` |

### 2.3.1 Estado por plato (`PRB-87`)
`PATCH /api/v1/pedidos/{id}/items/{itemId}/estado` — Request: `{"estado": "EN_PREPARACION"}`.

- Estados: `PENDIENTE` → `EN_PREPARACION` → `LISTO` → `SERVIDO`. Solo se acepta el **siguiente** paso.
- Response `200`: ítem actualizado, p. ej. `{"id": 7, "producto_id": 1, "producto_nombre": "...", "estacion": "COCINA", "cantidad": 1, "subtotal": 48.00, "modificadores": [], "estado": "EN_PREPARACION"}`.
- Cada línea de `GET /api/v1/pedidos` y `GET /api/v1/pedidos/{id}` incluye `estado` (por defecto `PENDIENTE`).
- Errores: `400` salto, retroceso, repetición o valor de `estado` desconocido (mensaje con el flujo permitido); `403` rol no permitido; `404` pedido inexistente o ítem que no pertenece al pedido; `409` pedido `COBRADO` o `ANULADO`.
- Cada cambio queda en la auditoría del pedido (`evento_pedido`) con el motivo `Ítem {id}: A → B`.
- Requiere la columna `detalle_pedido.estado_item VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE'` (`PRB-86`).

### 2.3.2 Cobro con Descuentos (`PRB-89`)
`POST /api/v1/mesas/{id}/cobrar` y `POST /api/v1/pedidos/{id}/cobrar`
- `CobrarRequest`: acepta campos opcionales `descuento` (monto fijo en S/), `tipo_descuento` (`"MONTO"` o `"PORCENTAJE"`) y `porcentaje_descuento` (0 a 100).
- `CobroResponse`: devuelve `total` (con descuento aplicado), `subtotal` (neto sin IGV), `igv` (18%), `descuento` (monto descontado), `pagado`, `vuelto` y `pedidos`.
- Validación: `totalConDescuento = totalConsumo - descuento`. No se permite descuento negativo ni mayor al total de la cuenta (400). Pagos se validan contra `totalConDescuento + propina` (400 si insuficiente).
- Auditoría: Si hay descuento, se registra en la auditoría del pedido (`evento_pedido`) con el usuario que ejecutó el cobro.

### 2.4 Monitoreo y Salud Operativa (`PRB-64`)
| Método | Ruta | Response | Auth |
|---|---|---|---|
| `GET` | `/api/v1/health` (`/health`) | `{status, db, version, uptime_seg}` | Público en LAN |
| `GET` | `/api/v1/version` (`/version`) | `{version, servicio, entorno}` | Público en LAN |

---

## 3. Lista de Recortes de Alcance Aceptada Explícitamente (v1 Producción)
1. **Facturación electrónica SUNAT (OSE/PSE):** Fuera de v1; el sistema emite pre-cuenta y comprobante interno de control.
2. **Pasarela de pagos integrada (Niubiz/Izipay):** Fuera de v1; pagos con tarjeta, Yape y Plin registran manualmente el número de operación/voucher.
3. **Recetas multinivel e insumos por gramo:** Recortado a control de stock directo por plato del día (`PRB-59`).
4. **Métricas Prometheus/Grafana en la nube:** Recortado a `/api/v1/health` + logs rotativos locales (`PRB-64`).

---

## 4. Definición de Terminado (DoD)
- Pruebas automatizadas en verde (`mvn test` con 0 fallos).
- Cobertura de código JaCoCo > 70% de líneas.
- Análisis estático sin errores (`0 Checkstyle violations`, `0 SpotBugs bugs`).
- Compatibilidad estricta con `spring.jpa.hibernate.ddl-auto: validate` contra `db/schema.sql` + `V2__produccion.sql`.
- Evidencia documental en `docs/evidencias/PRB-XX/CIERRE.md`.
