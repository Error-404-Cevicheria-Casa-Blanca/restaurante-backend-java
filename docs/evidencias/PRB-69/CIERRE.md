# Evidencia de Cierre — PRB-69: [P-23] E2E en tablet física contra backend real

- **Issue Jira:** `PRB-69`
- **Responsable:** Max Erick Muñoz Paredes
- **Rama:** `feature/PRB-69-go-live-y-cierre`
- **Estado:** COMPLETADO Y VERIFICADO

---

## 1. Matriz de Ejecución End-to-End (Sin Datos Mock)

| Paso | Operación en Tablet / Backend Real (`192.168.1.10:8080`) | Endpoint REST Invocado | Resultado Obtenido | Estado |
|---|---|---|---|---|
| 1 | Login rápido por PIN de 4 dígitos (Mozo 1) | `POST /api/v1/auth/pin` | `200 OK`, JWT de turno (12h) emitido | APROBADO |
| 2 | Carga del mapa real de 13 mesas físicas | `GET /api/v1/mesas` | `200 OK`, Mesas 1 a 13 ordenadas desde PostgreSQL | APROBADO |
| 3 | Apertura de Mesa 4 y envío de comanda con modificadores | `POST /api/v1/pedidos` | `201 Created`, Mesa 4 pasa a `OCUPADA`, ítems agrupados por estación (`COCINA`/`BAR`) | APROBADO |
| 4 | Colisión desde Tablet 2 intentando abrir la misma Mesa 4 | `PATCH /api/v1/mesas/4/estado` / `POST /api/v1/pedidos` | `409 Conflict` con cuerpo estructurado (`estado_actual: OCUPADA`, `mozo_actual`) | APROBADO |
| 5 | Corte Wi-Fi y reconexión enviando el mismo `client_request_id` | `POST /api/v1/pedidos` (`Idempotency-Key`) | `200 OK` (`replay: true`), `0` comandas duplicadas en PostgreSQL | APROBADO |
| 6 | Avance de preparación en KDS (`EN_PREPARACION` -> `LISTO` -> `SERVIDO`) | `PATCH /api/v1/pedidos/{id}/estado` | `200 OK`, transiciones auditadas | APROBADO |
| 7 | Solicitud de pre-cuenta e impresión térmica centralizada | `POST /api/v1/mesas/4/precuenta` | `200 OK`, Mesa 4 pasa a `CUENTA_SOLICITADA`, `estado_impresion: IMPRESO` | APROBADO |
| 8 | Cobro mixto (`EFECTIVO` + `YAPE`) y liberación de mesa | `POST /api/v1/mesas/4/cobrar` | `201 Created`, registros en tabla `pago`, pedido `COBRADO`, Mesa 4 `LIBRE` | APROBADO |

---

## 2. Pruebas Automatizadas de Respaldo

- `MesaConflicto409IntegrationTest` (Conflicto `409` entre dos tablets).
- `PedidoIdempotenciaAnulacionIntegrationTest` (Reconexión sin duplicados vía `client_request_id`).
- `MesaCobroIntegrationTest` y `ImpresionPrecuentaIntegrationTest` (Pre-cuenta, impresión, cobro y liberación de mesa).
