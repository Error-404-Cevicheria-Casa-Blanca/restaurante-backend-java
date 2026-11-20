# Evidencia de Cierre — PRB-114: Branch Protection, Secrets y Cierre de CI en los 3 Repositorios (ADR-08)

- **Issue Jira:** `PRB-114` (Subtarea de `PRB-22` / Epic `PRB-105` — Tareas `B10`, `B11`, `B08`)
- **Responsable:** Max Erick Muñoz Paredes
- **Rama:** `feature/PRB-113-114-ci-verify-y-runbook` → `develop`
- **Estado:** COMPLETADO Y VERIFICADO

---

## 1. Resumen de Implementación

1. **Auditoría y Configuración de Branch Protection y Secrets (`scripts/deploy/configure-branch-protection.ps1`):**
   - Verifica vía API REST de GitHub que `develop` y `main` están protegidas (`protected=True`) exigiendo Pull Request y al menos 1 aprobación (`required_approving_review_count >= 1`) en los 3 repositorios (`restaurante-backend-java`, `restaurante-admin-django`, `restaurante-app-kotlin`).
   - Confirma el registro de los 3 workflows de CI (`.github/workflows/ci.yml` en Java `PRB-113`, Django `PRB-112` y Android `PRB-111`) sin secretos hardcodeados en los repositorios.
2. **Runbook Operativo del Backend (`docs/produccion/RUNBOOK-BACKEND-OPERACION.md` — Tarea `B08`):**
   - Documenta el orden de arranque de los 5 servicios Windows, verificación de healthchecks HTTP/HTTPS (Caddy TLS), cola de impresión (`PRB-106`/`PRB-107`), backups y procedimiento de rollback (`rollback-release.ps1`).

---

## 2. Archivos de Evidencia Cruda Adjuntos

- `docs/evidencias/PRB-114/01-branch-protection.txt`: Salida cruda de `.\scripts\deploy\configure-branch-protection.ps1` consultando en vivo la API de GitHub.
- `docs/evidencias/PRB-114/02-git.txt`: Salida cruda de `git log --oneline -5`.
