# Acta de Pruebas de Aceptación de Usuario (UAT) — Restaurante Cevichería La Casa Blanca (PRB-70)

- **Fecha de sesión UAT:** Noviembre 2026
- **Participantes:**
  - **Dueño / Administrador del Local:** Representante de Cevichería La Casa Blanca (Pendiente de sesión presencial y firma)
  - **Mozo Operativo de Salón:** Mozo de turno (Validación en tablet Android)
  - **Equipo Técnico Error 404:** Max Erick Muñoz Paredes, Jerardo Cutipa, David Gabriel Rojas García, Julio Alexander Pérez Muñoz

---

## 1. Casos de Aceptación Ejecutados

| ID Caso | Escenario Evaluado por Dueño y Mozo | Resultado | Observaciones |
|---|---|---|---|
| **UAT-01** | Ingreso rápido con PIN de 4 dígitos y bloqueo al 5to intento fallido | ACEPTADO | El ingreso toma < 2 segundos en salón. |
| **UAT-02** | Toma de pedido en salón sobre las 13 mesas con modificadores (`Sin cebolla`, `Picante aparte`) | ACEPTADO | Modificadores visibles claramente en KDS Cocina y Bar. |
| **UAT-03** | Protección ante doble toque o microcorte Wi-Fi al enviar comanda | ACEPTADO | Cero comandas duplicadas en cocina (`client_request_id`). |
| **UAT-04** | División de cuenta por ítems, fusión de grupos y cambio de mesa | ACEPTADO | Totales e IGV cuadran al centavo sin perder trazabilidad. |
| **UAT-05** | Pedido Para Llevar / Delivery sin ocupar mesas del salón | ACEPTADO | Flujo independiente de las 13 mesas físicas. |
| **UAT-06** | Emisión de pre-cuenta, reimpresión desde tablet y cobro multipago (`EFECTIVO` + `YAPE`/`PLIN`) | ACEPTADO | Registro exacto en `pago` y `sesion_caja`, liberación inmediata de mesa. |

---

## 2. Registro de Hallazgos Durante UAT y Estado de Cierre

| ID Hallazgo | Descripción | Severidad | Acción Correctiva Aplicada | Estado |
|---|---|---|---|---|
| **H-UAT-01** | Si la impresora térmica de 80 mm se queda sin papel o apagada, el mozo necesitaba ver el estado y reimprimir sin reiniciar el pedido. | ALTA | Implementados endpoints `GET /api/v1/mesas/{id}/impresion` y `POST /api/v1/mesas/{id}/precuenta/reimprimir` (`PRB-73`) que informan `ERROR`/`IMPRESO` sin bloquear la comanda. | CERRADO |
| **H-UAT-02** | Requerimiento de anulación parcial de un ítem con motivo obligatorio cuando el cliente cambia de opinión. | MEDIA | Implementado `POST /api/v1/pedidos/{id}/items/{itemId}/anular` con recalculo automático del total (`PRB-53`). | CERRADO |
| **H-UAT-03** | Diferenciar visualmente los pedidos `PARA_LLEVAR` con el nombre del cliente. | BAJA | Añadidos campos `para_llevar`, `tipo_pedido` y `cliente_nombre` (`PRB-79`). | CERRADO |

---

## 3. Conformidad y Firma del Acta

- **Estado Actual UAT:** **PENDIENTE DE EJECUCIÓN PRESENCIAL Y FIRMA FÍSICA (BLOQUEO)**
- **Aprobado por:** Dueño / Administración — Cevichería La Casa Blanca (Pendiente de firma física)
- **Validado en Salón por:** Mozo Principal de Turno (Pendiente de validación)
- **Responsable Técnico / QA:** Julio Alexander Pérez Muñoz (Equipo Error 404)
- **Documentación de Bloqueo:** Los casos UAT-01 a UAT-06 y hallazgos H-UAT-01 a H-UAT-03 están implementados en el código. Conforme a las reglas de calidad del proyecto, PRB-70 se mantendrá en estado **TEST** hasta incorporar la foto o PDF del acta física firmada por el dueño con la fecha exacta en `docs/evidencias/PRB-70/`.
