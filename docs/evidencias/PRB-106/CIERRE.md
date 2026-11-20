# Evidencia de Cierre — PRB-106: [Java] Encolar comanda por estación y precuenta con mesa_id en impresion_cola (ADR-01 / ADR-07)

- **Issue Jira:** `PRB-106` (Subtarea de `PRB-58` / Epic `PRB-105` — Tareas `B01`, `B02`, `B03`)
- **Responsable:** Max Erick Muñoz Paredes
- **Rama:** `feature/PRB-106-impresion-real-cola` → `develop`
- **Estado:** COMPLETADO Y VERIFICADO

---

## 1. Resumen de Implementación (ADR-01 y ADR-07)

1. **`B03` — Columna `mesa_id` en entidad `TrabajoImpresion` (`src/main/java/com/lacasablanca/backend/model/TrabajoImpresion.java`):**
   - Se incorporó el campo `@Column(name = "mesa_id") private Integer mesaId` en `TrabajoImpresion` y métodos de consulta por `mesa_id`, `pedido_id`, `tipo` y `estacion` en `TrabajoImpresionRepository.java`.
2. **`B02` — Encolado automático de `COMANDA` por estación al crear o ampliar pedido (`PedidoService.java`):**
   - Al registrar o ampliar un pedido, se agrupan las estaciones operativas (`BARRA_FRIA`, `COCINA_CALIENTE`, `BEBIDAS`, `POSTRES`) de los ítems y se inserta (o reactiva si ya existe para respetar la unicidad `(pedido_id, tipo, estacion)`) una fila `tipo="COMANDA"` por estación en `impresion_cola` con `estado="PENDIENTE"`, `pedido_id`, `mesa_id` y `payload=""`.
3. **`B01` — Eliminación de `IMPRESO` simulado en pre-cuenta y lectura de estado real (`CobroService.java`):**
   - `POST /api/v1/mesas/{id}/precuenta` y `POST /api/v1/mesas/{id}/precuenta/reimprimir` insertan en `impresion_cola` una fila `tipo="PRECUENTA"` con `estado="PENDIENTE"`, `mesa_id`, `pedido_id` y `payload=""`.
   - `GET /api/v1/mesas/{id}/impresion` y `GET /api/v1/mesas/{id}/cuenta` consultan directamente el estado real de la fila en `impresion_cola` (`PENDIENTE`, `IMPRESO`, `FALLIDO`), eliminando la simulación en memoria.

---

## 2. Archivos de Evidencia Cruda Adjuntos

- `docs/evidencias/PRB-106/01-tests.txt`: Salida cruda de `.\mvnw.cmd test` (`117` tests ejecutados, `0` fallos).
- `docs/evidencias/PRB-106/02-git.txt`: Salida cruda de `git log --oneline -5`.
