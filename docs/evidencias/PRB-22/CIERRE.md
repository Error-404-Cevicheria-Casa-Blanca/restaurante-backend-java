# Evidencia de Cierre — PRB-22: Configuración de Pipeline de Integración Continua (CI) con GitHub Actions

- **Issue Jira:** `PRB-22`
- **Rama:** `feature/PRB-22-ci-github-actions`
- **Estado:** COMPLETADO Y VERIFICADO

---

## 1. Resumen del Pipeline CI Implementado (`.github/workflows/ci.yml`)

1. **Disparadores (`on: push` y `on: pull_request`):**
   - Se ejecuta automáticamente en cada `push` a `main`, `develop` y `feature/**`, así como en cada `Pull Request` hacia `main` y `develop`.
2. **Job Principal (`build-and-test-java`):**
   - **Entorno:** `ubuntu-latest` con `actions/setup-java@v4` (**Eclipse Temurin JDK 17**) y caché de dependencias Maven.
   - **Verificación integral (`mvn -B clean verify`):**
     - Compila el código fuente de Spring Boot 3.
     - Ejecuta auditoría **Checkstyle** (`0` violaciones permitidas).
     - Ejecuta las **94 pruebas unitarias y de integración** con JUnit 5 y MockMvc (`0` fallos).
     - Genera reporte de cobertura **JaCoCo** (`82.00%` > `70%`).
     - Ejecuta análisis estático de bytecode con **SpotBugs** (`0` bugs).
   - **Publicación de Artefactos (`actions/upload-artifact@v4`):**
     - Publica los reportes XML de JUnit (`target/surefire-reports/*.xml`) y el sitio HTML/CSV de cobertura JaCoCo (`target/site/jacoco/**`).
3. **Control de Calidad en Pull Requests:**
   - Si cualquier test, regla de Checkstyle o análisis de SpotBugs falla, el job retorna código distinto de cero y bloquea la integración en `develop`/`main`.
