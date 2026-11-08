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

### 2.1 Autenticación y Control de Acceso (`PRB-54`)
| Método | Ruta | Request | Response | Códigos HTTP | Roles |
|---|---|---|---|---|---|
| `POST` | `/api/v1/auth/login` | `{usuario, clave}` | `{token, tipo, usuario:{id, nombre, rol}}` | `200`, `400`, `401`, `423` | Público |
| `POST` | `/api/v1/auth/pin` | `{usuario, pin}` (4 dígitos) | `{token, tipo, usuario:{id, nombre, rol}}` | `200`, `400`, `401`, `423` | Público |
| `GET` | `/api/v1/auth/me` | — | `{usuario_id, roles}` | `200`, `401`, `403` | Todos |
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
| `POST` | `/api/v1/pedidos/{id}/cobrar` (`/pagos`) | `CobrarRequest` | `CobroResponse` | `201`, `400`, `409` | `MOZO`, `CAJERO`, `ADMIN` |

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
