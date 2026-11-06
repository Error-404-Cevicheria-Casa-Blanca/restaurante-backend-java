# Evidencia de Cierre — PRB-26: Pruebas de Aceptación de Usuario (UAT) y Validación del Flujo End-to-End

- **Issue Jira:** `PRB-26`
- **Estado:** COMPLETADO Y VERIFICADO

---

## 1. Matriz de Casos de Prueba de Aceptación (Flujo E2E)

### Caso `CP-UAT-01`: Flujo Completo de Restaurante (Admin Django -> Tablet Kotlin -> API Java -> Cobro)

| Paso | Acción Ejecutada | Componente | Resultado Obtenido | Estado |
|---|---|---|---|---|
| 1 | Admin crea/actualiza plato y disponibilidad en el panel web | `restaurante-admin-django` | Plato persistido en tabla `producto` de PostgreSQL (`casablanca`) | APROBADO |
| 2 | Mozo abre la app en la tablet y visualiza el plato actualizado en la carta | `restaurante-app-kotlin` (`GET /api/v1/productos`) | Catálogo sincronizado con precio y disponibilidad vigentes | APROBADO |
| 3 | Mozo asigna la Mesa 3 y envía comanda con 2 platos y 1 bebida | `POST /api/v1/pedidos` | `201 Created`, Mesa 3 pasa a `OCUPADA`, ítems separados por estación (`COCINA`/`BAR`) | APROBADO |
| 4 | Cocina toma el pedido y actualiza estado a `EN_PREPARACION` y `LISTO` | `PATCH /api/v1/pedidos/{id}/estado` | `200 OK`, evento de auditoría registrado | APROBADO |
| 5 | Mozo solicita la pre-cuenta desde la tablet con cálculo de IGV (18%) | `POST /api/v1/mesas/3/precuenta` | `200 OK`, subtotal + IGV 18% + propina sugerida y trabajo de impresión generado | APROBADO |
| 6 | Se registra el cobro y el panel Django consolida la venta en el dashboard del día | `POST /api/v1/mesas/3/cobrar` | `201 Created`, pago registrado en `pago` y `sesion_caja`, Mesa 3 liberada (`LIBRE`) | APROBADO |

---

### Caso `CP-UAT-02`: Manejo de Errores y Caídas de Red Wi-Fi

| Paso | Acción Ejecutada | Resultado Esperado | Resultado Obtenido | Estado |
|---|---|---|---|---|
| 1 | Simular pérdida de Wi-Fi en la tablet al enviar pedido y reintentar con el mismo `client_request_id` | No duplicar la orden en base de datos ni en KDS | Primer envío `201 Created`, reintento `200 OK` (`replay: true`), `1` sola comanda en BD | APROBADO |
| 2 | Dos tablets intentan ocupar simultáneamente la misma mesa libre | Solo una gana y la segunda recibe advertencia clara | `409 Conflict` con detalle del estado actual y mozo asignado | APROBADO |
