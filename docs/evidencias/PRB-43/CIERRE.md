# Cierre de Tarea - PRB-43 / PRB-55 (P-08: Pre-cuenta con IGV y Cobro Multi-Medio en Backend)

- **Rama:** `feature/PRB-43-precuenta-y-cobro`
- **Base:** `develop`
- **Tickets Jira:** PRB-43 / PRB-55 (P-08)
- **Responsable:** MP (Max Muñoz)

---

## 1. Alcance y Objetivos de la Tarea
Implementar en el Backend Java (Spring Boot 3.5.x) los endpoints transaccionales requeridos para el ciclo completo de pre-cuenta y cobro:
1. `GET /api/v1/mesas/{id}/cuenta`: Cálculo tributario según norma peruana D13 (Total con IGV 18% incluido, Subtotal e IGV discriminados, propina sugerida al 10%) e ítems de consumo consolidados por producto.
2. `POST /api/v1/mesas/{id}/precuenta`: Transición de la mesa de `OCUPADA` a `CUENTA_SOLICITADA` (o confirmación idempotente si ya está solicitada) y emisión del resumen de consumo.
3. `POST /api/v1/mesas/{id}/cobrar`: Liquidación de la comanda con soporte de pagos individuales o mixtos (`EFECTIVO`, `YAPE`, `PLIN`, `TARJETA`, `TRANSFERENCIA`), validación estricta de referencia en pagos electrónicos, cálculo de vuelto para efectivo, transición atómica de pedidos a `COBRADO`, liberación de la mesa a `LIBRE` con persistencia de `fecha_cierre` y control de idempotencia real mediante `client_request_id`.
4. `POST /api/v1/pedidos/{id}/pagos`: Liquidación individual por pedido con liberación automática de la mesa cuando todos sus pedidos han sido cobrados.

---

## 2. Componentes Implementados

### A. Capa de Transferencia de Datos (DTOs)
- `CuentaResponse`: Encapsula `mesa_id`, lista de `pedidos`, lista de `items`, `subtotal`, `igv`, `total` y `propina_sugerida`.
- `ItemCuentaDto`: Agrupa consumo por producto (`nombre`, `cantidad`, `precio_unitario`, `subtotal_linea`).
- `PedidoResumenCuentaDto`: Resumen de pedidos asociados (`id`, `estado`, `total`).
- `CobrarRequest`: Estructura para registrar pagos múltiples, `propina`, `observacion` y `client_request_id`.
- `PagoRequestDto`: Detalle por forma de pago (`metodo`, `monto`, `referencia`).
- `CobroResponse`: Resultado del cobro (`total`, `pagado`, `vuelto`, `pedidos`).

### B. Capa de Servicio (`CobroService`)
- Lógica de liquidación transaccional (`@Transactional`) e idempotencia por `client_request_id`.
- Validación de estado de mesa (impide cobros sobre mesas `LIBRE` arrojando `409 Conflict`).
- Desglose financiero D13: $\text{Subtotal} = \text{Total} / 1.18$, $\text{IGV} = \text{Total} - \text{Subtotal}$.
- Validación de suficiencia de pago, rechazo de descuadres en medios electrónicos y exigencia de número de operación.
- Liberación atómica de mesa y transición de pedidos a `COBRADO`.

### C. Capa de Controladores REST (`MesaController` y `PedidoController`)
- Rutas expuestas:
  - `GET /api/v1/mesas/{id}/cuenta`
  - `POST /api/v1/mesas/{id}/cobrar`
  - `POST /api/v1/mesas/{id}/precuenta`
  - `POST /api/v1/pedidos/{id}/pagos`
- Protegidos con JWT y roles autorizados (`MOZO`, `CAJERO`, `ADMIN`).

### D. Pruebas de Integración (`MesaCobroIntegrationTest`)
Batería de 12 casos de prueba automatizados con `MockMvc` y H2:
1. `obtenerCuentaRetornaDesgloseCorrecto`
2. `obtenerCuentaMesaLibreRetorna409`
3. `solicitarPrecuentaTransicionaEstadoMesa`
4. `cobrarConEfectivoExitoso`
5. `cobrarConYapeExitoso`
6. `cobrarConYapeSinReferenciaRetorna400`
7. `cobrarConMontoInsuficienteRetorna400`
8. `cobrarConMismoClientRequestIdEsIdempotente`
9. `cobrarConPagoMixtoYPropinaExitoso`
10. `cobrarConExcesoEnPagoDigitalRetorna400`
11. `cobrarPedidoIndividualExitoso`
12. `endpointsCobroSinTokenRetornan401`

---

## 3. Métricas de Calidad y Resultados

* **Total de Pruebas Automatizadas:** **64 de 64 pruebas en verde (0 fallos, 0 errores, BUILD SUCCESS)**.
* **Cobertura de Código (JaCoCo 0.8.12):**
  - **84.07% en Líneas** (802 de 954) - *Supera ampliamente el umbral del 70%*.
  - **84.24% en Instrucciones** (3,101 de 3,681).
  - **69.80% en Ramas** (141 de 202).
* **Análisis Estático de Código:**
  - **Checkstyle 3.4.0:** 0 errores bloqueantes de estilo (`You have 0 Checkstyle violations`, con advertencias de espaciado estilístico preexistentes).
  - **SpotBugs 4.8.6.6:** 0 bugs detectados (`BugInstance size is 0`).

---

## 4. Evidencias Generadas
- `docs/evidencias/PRB-43/` y `docs/evidencias/PRB-55/`:
  - `01-tests.txt`: Transcripción de ejecución de la suite completa de 64 pruebas en Maven.
  - `02-cobertura.txt`: Resumen ejecutivo de métricas JaCoCo.
  - `03-git.txt`: Estadísticas de cambios frente a `origin/develop`.
  - `CIERRE.md`: Este reporte técnico de cierre.
