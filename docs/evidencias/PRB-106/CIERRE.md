# Evidencia de Cierre — PRB-106: [Java] Encolar comanda por estación y precuenta con mesa_id en impresion_cola (ADR-01 / ADR-07)

- **Issue Jira:** `PRB-106` (Subtarea de `PRB-58` / Epic `PRB-105` — Tareas `B01`, `B02`, `B03`)
- **Responsable:** Max Erick Muñoz Paredes
- **Rama:** `feature/PRB-106-impresion-real-cola` → `develop`
- **Estado:** COMPLETADO Y VERIFICADO

---

## 1. Resumen de Implementación y Decisión Técnica Explícita (ADR-01 y ADR-07)

1. **`B03` — Resolución de `mesa_id` vía `pedido_id` + `payload = '{"mesa_id":N}'` (Decisión de integración Java ↔ Django `PRB-107`):**
   - **Decisión explícita del equipo (`PRB-106` + `PRB-107` commit `12ff3e9` en `restaurante-admin-django`):** Para mantener compatibilidad estricta con `spring.jpa.hibernate.ddl-auto: validate` y el DDL oficial en `db/schema.sql` sin alterar la estructura física de `impresion_cola`, `TrabajoImpresion.mesaId` se declara `@Transient` en Java (derivado de `pedido.getMesa().getId()` o extraído del `payload`) y al encolar un trabajo `PRECUENTA` se persiste `pedido_id` (si hay pedido activo) junto con `payload = "{\"mesa_id\":" + mesaId + "}"`.
   - El worker de Django (`catalogo/impresion_service.py:extraer_referencia_payload` y `resolver_pedido_para_trabajo`, fusionado en `restaurante-admin-django` PR #15) consume directamente tanto `pedido_id` como `payload = '{"mesa_id":N}'` para resolver la mesa y generar el ticket ESC/POS de 48 columnas.
2. **`B02` — Encolado automático de `COMANDA` por estación al crear o ampliar pedido (`PedidoService.java`):**
   - Al registrar o ampliar un pedido, se agrupan las estaciones operativas (`BARRA_FRIA`, `COCINA_CALIENTE`, `BEBIDAS`, `POSTRES`) de los ítems y se consulta `findByPedidoIdAndTipoAndEstacionOrderByFechaCreacionDescIdDesc(pedidoId, "COMANDA", estacion)` (retornando `List<TrabajoImpresion>` y tomando la fila más reciente para evitar `IncorrectResultSizeDataAccessException` si hubo reimpresiones previas desde Django).
   - Si no existe fila previa para la estación, inserta `tipo="COMANDA"` con `estado="PENDIENTE"`, `pedido_id` y `payload=""`; si ya existe, reactiva la más reciente a `estado="PENDIENTE"`.
3. **`B01` — Eliminación de `IMPRESO` simulado en pre-cuenta y consulta indexada (`CobroService.java`):**
   - `POST /api/v1/mesas/{id}/precuenta` y `POST /api/v1/mesas/{id}/precuenta/reimprimir` insertan en `impresion_cola` una fila `tipo="PRECUENTA"` con `estado="PENDIENTE"`, `pedido_id` y `payload="{\"mesa_id\":" + mesaId + "}"`.
   - `buscarUltimoTrabajoPrecuentaEnBd` utiliza consultas filtradas por repositorio (`findByTipoAndPedidoMesaIdOrderByFechaCreacionDescIdDesc` y `findByTipoAndPayloadContainingOrderByFechaCreacionDescIdDesc`) en lugar de `findAll()`, devolviendo el estado real desde `impresion_cola` (`PENDIENTE`, `IMPRESO`, `FALLIDO`).

---

## 2. Archivos de Evidencia Cruda Adjuntos

- `docs/evidencias/PRB-106/01-tests.txt`: Salida cruda de `.\mvnw.cmd test` (`119` tests ejecutados, `0` fallos).
- `docs/evidencias/PRB-106/02-git.txt`: Salida cruda de `git log --oneline -5`.
