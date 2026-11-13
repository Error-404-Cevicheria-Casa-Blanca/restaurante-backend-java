# Evidencia de Cierre — PRB-16: Revisión del Sprint 4, etiquetado de versión MVP v0.1.0 y retrospectiva de equipo

- **Issue Jira:** `PRB-16`
- **Estado:** COMPLETADO Y VERIFICADO
- **Tag publicado en los 3 repositorios:** `v0.1.0`

---

## 1. Resumen de la Revisión del Sprint 4 (MVP `v0.1.0`)

1. **Entregables demostrados en el MVP (`v0.1.0`):**
   - **Backend Spring Boot (`restaurante-backend-java`):** Autenticación JWT (`POST /api/v1/auth/login`), consulta y actualización del mapa de 13 mesas en tiempo real (`GET /api/v1/mesas`, `PATCH /api/v1/mesas/{id}/estado` con control de concurrencia `409 Conflict`), y registro transaccional de comandas con desglose tributario IGV 18% y agrupación por estaciones de cocina (`POST /api/v1/pedidos`).
   - **Panel Web Django (`restaurante-admin-django`):** Modelo Entidad-Relación en PostgreSQL, datos semilla del catálogo marino y gestión web de carta, precios y disponibilidad del día.
   - **App Móvil Android (`restaurante-app-kotlin`):** Login con sesión JWT, mapa interactivo de 13 mesas con estados por colores, catálogo de platos, carrito con cálculo de IGV y envío de comandas.

---

## 2. Retrospectiva de Equipo (Sprint 4)

| Dimensión | Hallazgos del Equipo Error 404 | Acción de Mejora Aplicada |
|---|---|---|
| **Qué salió bien** | Integración fluida entre la app Android, el backend Spring Boot y PostgreSQL; pruebas de integración con MockMvc detectaron regresiones a tiempo. | Mantener cobertura JaCoCo `> 80%`, Checkstyle y SpotBugs obligatorios en CI. |
| **Qué se puede mejorar** | Sincronizar antes los cambios de esquema SQL entre Django y las entidades JPA del backend para evitar fallos con `ddl-auto: validate`. | Congelar el Contrato API v2 (`PRB-49`) y centralizar las migraciones SQL en `V2__produccion.sql` (`PRB-51`). |
| **Acciones para Producción** | Incorporar idempotencia (`client_request_id`) ante microcortes Wi-Fi en el salón y cobro multipago. | Ejecutado en `PRB-53`, `PRB-55`, `PRB-68` y `PRB-82`. |
