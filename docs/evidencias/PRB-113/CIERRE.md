# Evidencia de Cierre — PRB-113: Workflow GitHub Actions Java con `mvn verify` y cobertura JaCoCo (ADR-08)

- **Issue Jira:** `PRB-113` (Subtarea de `PRB-22` / Epic `PRB-105` — Tareas `B06`, `B07`)
- **Responsable:** Max Erick Muñoz Paredes
- **Rama:** `feature/PRB-113-114-ci-verify-y-runbook` → `develop`
- **Estado:** COMPLETADO Y VERIFICADO

---

## 1. Resumen de Implementación

1. **Workflow de GitHub Actions (`.github/workflows/ci.yml`):**
   - Ejecuta `./mvnw -B clean verify` sobre `ubuntu-latest` con JDK 17 (Temurin), validando compilación, suite completa de pruebas (`118` tests), Checkstyle (`0` violaciones), SpotBugs (`0` bugs) y reporte de cobertura JaCoCo.
   - Publica automáticamente el resumen de cobertura JaCoCo (Instrucciones, Líneas y Ramas) en `$GITHUB_STEP_SUMMARY` y sube los artefactos `target/surefire-reports/*.xml` y `target/site/jacoco/**`.
2. **Actualización de JaCoCo a `0.8.13` (`pom.xml`):**
   - Soporta instrumentación limpia sin advertencias tanto en JDK 17 (CI) como en JDK 24 (desarrollo local) y ejecuta `jacoco:report` en las fases `test` y `verify`.

---

## 2. Archivos de Evidencia Cruda Adjuntos

- `docs/evidencias/PRB-113/01-mvn-verify.txt`: Salida cruda completa de `.\mvnw.cmd clean verify` (`118` tests, `0` fallos, `0` Checkstyle, `0` SpotBugs).
- `docs/evidencias/PRB-113/02-jacoco-cobertura.txt`: Resumen crudo calculado desde `target/site/jacoco/jacoco.csv`.
- `docs/evidencias/PRB-113/03-git.txt`: Salida cruda de `git log --oneline -5`.
