# Evidencia de Cierre — PRB-24: Definición de Criterios de Rendimiento y Plan de Pruebas de Carga

- **Issue Jira:** `PRB-24`
- **Estado:** COMPLETADO Y VERIFICADO

---

## 1. Umbrales y Criterios de Aceptación de Rendimiento (SLA en Red LAN)

| Métrica | Umbral Objetivo (SLA) | Resultado Medido en Ejecución (`PRB-68`) | Estado |
|---|---|---|---|
| **Latencia Percentil 95 (`p95`) en lectura (`GET /mesas`, `GET /pedidos`)** | `< 150 ms` | `38 ms - 49 ms` | CUMPLE |
| **Latencia Percentil 95 (`p95`) en escritura transaccional (`POST /pedidos`)** | `< 300 ms` | `112 ms` (`p95 global = 64 ms`) | CUMPLE |
| **Tasa de error HTTP (`5xx` / timeouts) bajo carga nominal y pico** | `0.0%` | `0.0%` (`2,080` requests exitosos) | CUMPLE |
| **Duplicación de comandas ante reintentos por microcorte Wi-Fi** | `0 duplicados` | `0 duplicados` (`Idempotency-Key`) | CUMPLE |

---

## 2. Escenarios del Plan de Pruebas de Carga (`Locust`)

1. **Escenario 1 — Operación Nominal de Salón:**
   - 2 tablets Android consultando el mapa de 13 mesas cada 4 segundos (`GET /api/v1/mesas`) y 2 pantallas KDS consultando pedidos pendientes (`GET /api/v1/pedidos?estado=PENDIENTE`).
2. **Escenario 2 — Pico de Hora Punta (Almuerzo Fin de Semana):**
   - 20 hilos concurrentes enviando comandas con modificadores (`POST /api/v1/pedidos`), solicitando pre-cuentas (`POST /api/v1/mesas/{id}/precuenta`) y liquidando cobros (`POST /api/v1/mesas/{id}/cobrar`).
3. **Escenario 3 — Resiliencia ante Microcortes Wi-Fi:**
   - Reenvío inmediato de `POST /api/v1/pedidos` con el mismo `client_request_id` verificando respuesta `200 OK` (`replay: true`).
   - Script ejecutable: `scripts/loadtest/locustfile_produccion_lan.py`.
