# Cierre de Tarea — PRB-21 (Sprint 6: Pruebas de Integración en Backend Java con MockMvc y Base de Datos de Prueba)

- **Rama:** `feature/PRB-21-pruebas-integracion-mockmvc`
- **Base:** `develop`
- **Responsable:** MP (Max Muñoz)
- **Ticket Jira:** PRB-21

---

## 1. Alcance y Objetivos de la Tarea
Implementar la suite formal de pruebas de integración para el Backend Java (Spring Boot 3.5.x) utilizando `MockMvc` contra la base de datos de prueba en memoria H2 (`application-test.yml`), asegurando la cobertura integral de los endpoints REST, seguridad JWT, transaccionalidad de pedidos, ciclo de vida de mesas y validaciones de negocio.

---

## 2. Componentes de Prueba Implementados

### A. Pruebas de Integración de Controladores REST (`MockMvc` + H2)
1. **`CategoriaControllerTest` (Integración):**
   - `GET /api/v1/categorias` con autenticación JWT: valida código 200 OK, formato JSON y retorno del catálogo inicial de 4 categorías.
   - `GET /api/v1/categorias` sin token: valida código 401 Unauthorized y estructura estándar de error.
2. **`ProductoControllerTest` (Integración):**
   - `GET /api/v1/productos` sin filtro: valida 200 OK con los 4 productos de la carta y sus campos (precio, disponible, estación).
   - `GET /api/v1/productos?categoria_id=X`: valida 200 OK filtrando únicamente los productos de la categoría solicitada.
   - `GET /api/v1/productos?categoria_id=99999`: valida 200 OK retornando array vacío `[]`.
   - `GET /api/v1/productos` sin token: valida 401 Unauthorized.

### B. Prueba Integral de Flujo Operativo E2E (`FlujoRestauranteIntegrationTest`)
Prueba de integración end-to-end que recrea el flujo de atención completo sobre la base de datos de prueba:
1. **Login de Mozo:** `POST /api/v1/auth/login` con credenciales válidas $\to$ obtención de JWT.
2. **Consulta de Mesas:** `GET /api/v1/mesas` $\to$ verificación de Mesa 5 en estado `LIBRE`.
3. **Apertura de Mesa:** `PATCH /api/v1/mesas/5/estado` a `ATENDIDA` $\to$ persistencia en H2 de `mozo_id` y `fecha_apertura`.
4. **Consulta de Carta:** `GET /api/v1/productos` $\to$ selección de platos para la comanda.
5. **Creación Transaccional de Pedido:** `POST /api/v1/pedidos` (2 Ceviches + 1 Chicha Morada):
   - Valida cálculo financiero D13: $\text{Total} = 111.00$, $\text{Subtotal} = 94.07$, $\text{IGV} = 16.93$.
   - Valida agrupación automática por estaciones operativas (`BARRA_FRIA`, `BEBIDAS`).
   - Valida transición atómica de la Mesa a `OCUPADA` en base de datos.
   - Valida persistencia en `PedidoRepository` y `DetallePedidoRepository`.
6. **Prevención de Colisión Concurrente:** Intento de apertura sobre mesa ocupada $\to$ 409 Conflict.
7. **Pre-cuenta:** `PATCH /api/v1/mesas/5/estado` a `CUENTA_SOLICITADA`.
8. **Bloqueo de Modificación:** Intento de agregar nuevos pedidos sobre mesa con cuenta solicitada $\to$ 409 Conflict.
9. **Cobro y Liberación de Mesa:** `PATCH /api/v1/mesas/5/estado` a `LIBRE` $\to$ persistencia de `fecha_cierre` ($\ge$ `fecha_apertura`).

### C. Pruebas Unitarias de Servicios
1. **`CategoriaServiceTest`:** Mapeo entidad-DTO y desacoplamiento de repositorio.
2. **`ProductoServiceTest`:** Mapeo de DTOs, filtros por categoría y lógica de resolución de estaciones (`BARRA_FRIA`, `COCINA_CALIENTE`, `BEBIDAS`, `POSTRES`).

---

## 3. Métricas de Calidad y Resultados

* **Total de Pruebas Automatizadas:** **52 de 52 pruebas en verde (0 fallos, 0 errores, BUILD SUCCESS)**.
* **Cobertura de Código (JaCoCo 0.8.12):**
  - **85.13% en Líneas** (578 de 679) — *Supera holgadamente el criterio de aceptación del 70%*.
  - **84.43% en Instrucciones** (2,228 de 2,639).
  - **74.17% en Ramas** (89 de 120).
* **Análisis Estático:**
  - **Checkstyle 3.4.0:** 0 violaciones de estilo.
  - **SpotBugs 4.8.6.6:** 0 bugs detectados.

---

## 4. Evidencias Generadas
- `docs/evidencias/PRB-21/01-tests.txt`: Transcripción completa de ejecución de los 52 tests vía Maven.
- `docs/evidencias/PRB-21/02-cobertura.txt`: Resumen ejecutivo de métricas JaCoCo.
- `docs/evidencias/PRB-21/03-git.txt`: Estadísticas de cambios git frente a `origin/develop`.
- `docs/evidencias/PRB-21/CIERRE.md`: Este documento de cierre técnico.
