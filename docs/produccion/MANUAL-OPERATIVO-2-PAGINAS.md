# Manual Operativo Rápido (2 Páginas) y Plan de Contingencia — Cevichería La Casa Blanca (PRB-71)

---

## PÁGINA 1: OPERACIÓN DIARIA EN SALÓN, COCINA Y CAJA

### 1. Apertura del Local (10:30 AM)
1. Encender la **PC Central de Caja** (conectada a la UPS) y el **Router Wi-Fi LAN**.
2. Los servicios `CasaBlancaPostgres`, `CasaBlancaBackendJava` (`:8080`) y `CasaBlancaAdminDjango` (`:8000`) inician automáticamente con Windows.
3. Verificar en el navegador de Caja `http://localhost:8000` e iniciar la **Sesión de Caja** con el monto inicial de sencillo.
4. Verificar que la impresora térmica de 80 mm tenga rollo de papel y luz de estado encendida (`READY`).

### 2. Flujo del Mozo en Tablet Android
1. **Ingreso Rápido:** Digitar su **PIN de 4 dígitos** (cuidado: 5 intentos fallidos bloquean la cuenta por 15 minutos; el Administrador puede desbloquearla).
2. **Abrir Mesa y Enviar Comanda:**
   - Seleccionar una mesa `LIBRE` (verde) entre las **13 mesas**.
   - Agregar platos y bebidas, eligiendo **modificadores** (`Sin cebolla`, `Extra ají`, etc.) si el cliente lo solicita.
   - Pulsar **Enviar Comanda**. El pedido viaja automáticamente a las pantallas KDS de **Cocina** y **Bar**.
3. **Operaciones Especiales de Mesa:**
   - **Dividir Cuenta:** Permite separar ítems en una sub-cuenta independiente para cobro individual.
   - **Fusionar / Transferir:** Permite unir dos mesas o mudar a los clientes a otra mesa libre sin perder el historial.
   - **Pedido Para Llevar:** Usar el botón **Nuevo Pedido Para Llevar**, ingresando el nombre del cliente sin ocupar mesas del salón.
4. **Pre-Cuenta y Cobro:**
   - Pulsar **Solicitar Pre-Cuenta** (`POST /api/v1/mesas/{id}/precuenta`). Si la impresora no tenía papel, colocar rollo y pulsar **Reimprimir**.
   - Registrar el cobro (`EFECTIVO`, `YAPE`, `PLIN` o `TARJETA`). La mesa vuelve automáticamente a estado `LIBRE`.

### 3. Cierre del Turno (11:30 PM)
1. Realizar el **Arqueo y Cierre de Caja** en el panel comparando efectivo físico vs. `monto_esperado`.
2. A las **23:30**, el sistema ejecuta automáticamente el backup diario (`CasaBlanca-DailyBackup`) en `C:\CasaBlanca\backups`.

---

## PÁGINA 2: PLAN DE CONTINGENCIA EN PAPEL Y CONTACTOS DE SOPORTE

### 1. Qué hacer ante incidentes en pleno servicio

| Incidente | Acción Inmediata (Menos de 1 minuto) | Recuperación |
|---|---|---|
| **Impresora sin papel o apagada** | La comanda **NO se pierde ni se bloquea**. Reemplazar rollo / encender impresora y tocar **Reimprimir Pre-Cuenta** en la tablet. | Inmediata desde la tablet. |
| **Microcorte de Wi-Fi en salón** | La tablet reintenta con el mismo código único (`Idempotency-Key`) sin duplicar platos en cocina. | Automática al reconectar Wi-Fi. |
| **Corte de energía eléctrica en la cuadra** | La **UPS** mantiene encendidos el Router Wi-Fi y la PC Central durante **25–30 minutos** para cerrar cobros activos. | Si el corte supera 20 min, pasar a **Talonario Físico de Contingencia** numerado en Caja. |
| **Reinicio de emergencia de servicios** | En el escritorio de la PC Central, ejecutar como Administrador `C:\CasaBlanca\scripts\deploy\install-windows-services.ps1` o reiniciar `CasaBlancaBackendJava` en `services.msc`. | Menos de 10 segundos. |

### 2. Directorio de Soporte Técnico (War Room & Post Go-Live)

- **Líder Técnico / Backend & Infraestructura:** Max Erick Muñoz Paredes (`max.munoz@tecsup.edu.pe`)
- **Base de Datos & Backoffice Django:** Jerardo (Equipo Error 404)
- **App Móvil Android (Tablets):** Franco / Luis (Equipo Error 404)
- **Horario de Cobertura Directa (War Room):** 21/11 al 24/11 (Presencial y remoto en horario completo de atención).
