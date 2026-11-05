# Cierre de Tarea - PRB-44 (Sprint 7: Prueba de integración de conflicto 409 en mesas)

- **Rama:** `feature/PRB-44-conflicto-409-mesas`
- **Base:** `develop`
- **Ticket Jira:** PRB-44
- **Criterio de Aceptación:** "Añadir una prueba de integración que ejecute dos PATCH simultáneos sobre la misma mesa y verifique que la segunda solicitud recibe 409 Conflict. Usar la base de datos de prueba y limpiar el estado entre ejecuciones. Criterio de aceptación: la prueba es determinista y forma parte de la suite del backend."

---

## 1. Alcance y Objetivos de la Tarea
Diseñar, implementar y automatizar una prueba de integración rigurosa y determinista que valide la resolución de colisiones y condiciones de carrera concurrentes en la transición de estados de mesas (`PATCH /api/v1/mesas/{id}/estado`).

La prueba debe garantizar que:
1. Al enviar dos peticiones `PATCH` concurrentes exactamente al mismo instante para aperturar la misma mesa (`LIBRE` $\to$ `ATENDIDA`), exactamente una resulte exitosa (`200 OK`) y la segunda sea rechazada con `409 Conflict`.
2. Se utilice la base de datos de prueba en memoria H2 (`application-test.yml`).
3. Se garantice el aislamiento absoluto y limpieza de estado entre ejecuciones para asegurar determinismo.
4. Se integre limpiamente en la suite de pruebas del backend sin degradar la cobertura ni violar reglas de calidad (Checkstyle y SpotBugs).

---

## 2. Componente de Prueba Implementado: `MesaConflicto409IntegrationTest`

Ubicación: `src/test/java/com/lacasablanca/backend/controller/MesaConflicto409IntegrationTest.java`

Anotaciones y Configuración:
- `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)`
- `@AutoConfigureMockMvc`
- `@ActiveProfiles("test")`

### A. Mecanismo de Determinismo Concurrente
Para evitar condiciones de carrera indeterminadas o pruebas "flaky", se implementó un esquema de sincronización de hilos basado en Java Concurrency (`java.util.concurrent`):
- **Pool de hilos:** `ExecutorService pool = Executors.newFixedThreadPool(2)`.
- **Barreras de sincronización (`CountDownLatch`):**
  - `CountDownLatch listos = new CountDownLatch(2)`: Asegura que ambos hilos hayan inicializado su payload y contexto `MockMvc`.
  - `CountDownLatch salida = new CountDownLatch(1)`: Barrera de disparo simultáneo que libera a ambos hilos al mismo milisegundo mediante `salida.countDown()`.
- **Ejecución concurrentes sobre `MockMvc`:** Cada hilo ejecuta la petición HTTP real a través del dispatcher de Spring:
  ```java
  mockMvc.perform(patch("/api/v1/mesas/" + MESA_PRUEBA_NUMERO + "/estado")
          .header("Authorization", "Bearer " + tokenMozo)
          .contentType(MediaType.APPLICATION_JSON)
          .content(objectMapper.writeValueAsString(payload)))
         .andReturn();
  ```
- **Aserción de Resultados:**
  - `assertEquals(1, Collections.frequency(codigosHttp, 200))`
  - `assertEquals(1, Collections.frequency(codigosHttp, 409))`
  - Inspección del cuerpo JSON del error 409 para verificar estructura y mensaje informativo.
  - Verificación directa en base de datos H2 (`MesaRepository`) confirmando que la mesa 6 quedó en estado `ATENDIDA`, vinculada a `mozo_01` y con `fecha_apertura` registrada.

### B. Pruebas Adicionales de Validación de Estado (409 Conflict)
1. **`patchMismoEstadoAtendidaRetorna409()`:** Verifica que enviar un `PATCH` a un estado en el que la mesa ya se encuentra (`ATENDIDA` $\to$ `ATENDIDA`) retorna `409 Conflict` con mensaje `"La mesa 6 ya se encuentra en estado ATENDIDA"`.
2. **`patchTransicionInvalidaDesdeLibreRetorna409()`:** Verifica que saltarse pasos del flujo (p. ej. intentar solicitar cuenta desde `LIBRE`: `LIBRE` $\to$ `CUENTA_SOLICITADA`) es rechazado con `409 Conflict`.

### C. Aislamiento y Limpieza de Estado
- `@BeforeEach` y `@AfterEach` restablecen la mesa 6 a estado `LIBRE`, con `mozo = null`, `fechaApertura = null` y `fechaCierre = null`.
- Esto garantiza determinismo total y previene efectos colaterales sobre cualquier otra prueba de la suite.

---

## 3. Métricas de Calidad y Resultados

* **Total de Pruebas Automatizadas:** **55 de 55 pruebas en verde (0 fallos, 0 errores, BUILD SUCCESS)**.
* **Cobertura de Código (JaCoCo 0.8.12):**
  - **85.13% en Líneas** (578 de 679) - *Supera ampliamente el umbral del 70%*.
  - **84.43% en Instrucciones** (2,228 de 2,639).
  - **74.17% en Ramas** (89 de 120).
* **Análisis Estático de Código:**
  - **Checkstyle 3.4.0:** 0 violaciones de estilo (`You have 0 Checkstyle violations`).
  - **SpotBugs 4.8.6.6:** 0 bugs detectados (`BugInstance size is 0`).

---

## 4. Evidencias Generadas
- `docs/evidencias/PRB-44/01-tests.txt`: Salida completa de ejecución de la suite de 55 pruebas unitarias y de integración.
- `docs/evidencias/PRB-44/02-cobertura.txt`: Resumen ejecutivo de métricas de cobertura JaCoCo.
- `docs/evidencias/PRB-44/03-git.txt`: Estadísticas de cambios git frente a `origin/develop`.
- `docs/evidencias/PRB-44/CIERRE.md`: Este reporte técnico de cierre.
