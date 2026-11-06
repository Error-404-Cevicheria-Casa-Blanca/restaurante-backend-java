# Evidencia de Cierre — PRB-66: [P-20] Backups automáticos y restore probado

- **Issue Jira:** `PRB-66`
- **Responsable:** Max Erick Muñoz Paredes
- **Rama:** `feature/PRB-65-infraestructura-y-produccion`
- **Estado:** COMPLETADO Y VERIFICADO

---

## 1. Entregables Implementados

1. **Script de Backup Diario (`scripts/backup/backup-casablanca.ps1`):**
   - Ejecuta `pg_dump -Fc` sobre la base de datos `casablanca` generando archivos `.dump` con timestamp y hash `SHA256`.
   - Empaqueta la carpeta `media` del catálogo de platos en un archivo `.zip` fechado.
   - Aplica política de retención automática de **30 días**, eliminando archivos expirados y registrando cada ejecución en `backup-execution.log`.
2. **Script de Restore y Verificación (`scripts/backup/restore-casablanca.ps1`):**
   - Restaura cualquier `.dump` sobre una base de datos limpia (`casablanca_restore_test` o `casablanca`) mediante `pg_restore --clean --if-exists` y verifica el conteo de filas en `mesa` (13), `usuario`, `producto`, `pedido` y `pago`.
3. **Registro en Programador de Tareas de Windows (`scripts/backup/register-scheduled-task.ps1`):**
   - Registra la tarea `CasaBlanca-DailyBackup` para ejecutarse diariamente a las `23:30` (cierre de turno).
4. **Bitácora de Ejecución y Simulacro de Restore (`docs/evidencias/PRB-66/backup-execution.log`):**
   - Evidencia del dump generado, hash `SHA256` y simulacro de restauración exitosa con las 13 mesas e historial de pedidos y pagos íntegros.
