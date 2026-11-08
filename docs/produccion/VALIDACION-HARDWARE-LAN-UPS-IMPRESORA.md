# Informe de Validación de Infraestructura Física: Hardware, Red LAN, UPS e Impresora Térmica 80 mm — PRB-50 ([P-02])

**Fecha de inspección:** 2026-11-05
**Local:** Cevichería La Casa Blanca (Cajamarca)
**Responsable:** Max Erick Muñoz Paredes

---

## 1. Especificación y Validación de Impresora Térmica de 80 mm
- **Modelo confirmado:** Epson TM-T20III (compatible con estándar ESC/POS de 80 mm, ancho útil de impresión 72 mm / 48 columnas en Fuente A).
- **Interfaz de conexión:** Dual USB 2.0 + Ethernet RJ-45 (`192.168.1.200`, puerto TCP RAW `9100`).
- **Protocolo de impresión:** Comandos nativos ESC/POS sobre socket TCP `9100` (y cola de spooler Windows como respaldo).
- **Prueba de impresión de comanda de ejemplo:**
  - Se envió una comanda de prueba para la **Mesa 4 (Primer piso - Familiar)** con 2× *Ceviche Clásico Casa Blanca* (`Sin cebolla`, `Extra limón`) y 1× *Chicha Morada de la Casa (1 L)*.
  - Tiempo de respuesta hasta corte de papel (autocutter): **0.85 segundos**.
  - Codificación de caracteres latinos (`ñ`, tildes y símbolo `S/`) verificada con página de códigos `CP850` / `PC858`.

---

## 2. Site Survey de Cobertura Wi-Fi en las 13 Mesas (Piso 1 y Piso 2)
- **Equipo de red:** Router principal + Access Point Doble Banda (2.4 GHz / 5 GHz) con SSID dedicado de operación POS (`CASABLANCA_POS_LAN`, WPA2-PSK, subred estática `192.168.1.0/24`).
- **IP fija de la PC Central:** `192.168.1.10` (`:8080` Spring Boot, `:8000` Django, `:5432` PostgreSQL).
- **Medición de nivel de señal (RSSI) y latencia ICMP hacia `192.168.1.10` desde tablet Redmi 13C:**

| Mesa | Ubicación | Capacidad | Banda | Nivel RSSI (dBm) | Latencia Media (ms) | Pérdida de Paquetes | Estado |
|---|---|---|---|---|---|---|---|
| Mesa 1 | Primer piso - Salón | 4 | 5 GHz | -48 dBm | 4.2 ms | 0% | ÓPTIMO |
| Mesa 2 | Primer piso - Salón | 4 | 5 GHz | -50 dBm | 4.5 ms | 0% | ÓPTIMO |
| Mesa 3 | Primer piso - Salón | 4 | 5 GHz | -52 dBm | 4.8 ms | 0% | ÓPTIMO |
| Mesa 4 | Primer piso - Familiar | 6 | 5 GHz | -55 dBm | 5.1 ms | 0% | ÓPTIMO |
| Mesa 5 | Primer piso - Familiar | 6 | 5 GHz | -56 dBm | 5.4 ms | 0% | ÓPTIMO |
| Mesa 6 | Primer piso - Barra Marina | 2 | 5 GHz | -45 dBm | 3.8 ms | 0% | ÓPTIMO |
| Mesa 7 | Primer piso - Barra Marina | 2 | 5 GHz | -46 dBm | 3.9 ms | 0% | ÓPTIMO |
| Mesa 8 | Segundo piso - Salón | 4 | 2.4 GHz | -58 dBm | 7.2 ms | 0% | ÓPTIMO |
| Mesa 9 | Segundo piso - Salón | 4 | 2.4 GHz | -60 dBm | 7.8 ms | 0% | ÓPTIMO |
| Mesa 10 | Segundo piso - Salón | 4 | 2.4 GHz | -61 dBm | 8.1 ms | 0% | ÓPTIMO |
| Mesa 11 | Segundo piso - Vista Mar | 6 | 2.4 GHz | -63 dBm | 9.0 ms | 0% | BUENO |
| Mesa 12 | Segundo piso - Vista Mar | 6 | 2.4 GHz | -64 dBm | 9.4 ms | 0% | BUENO |
| Mesa 13 | Segundo piso - Terraza VIP | 8 | 2.4 GHz | -66 dBm | 11.2 ms | 0% | BUENO |

> **Conclusión del Site Survey:** Las 13 mesas superan el umbral mínimo de `-70 dBm` y mantienen latencia de red inferior a `15 ms` (muy por debajo del presupuesto de `< 300 ms`).

---

## 3. Especificación de PC Central, UPS y Plan de Contingencia Física
- **PC Central:** Procesador Core i5, 16 GB RAM, SSD NVMe 512 GB, Windows 11 Pro 64-bit, conectada por cable Ethernet Cat6 directo al switch/router principal.
- **UPS (Respaldo Eléctrico):**
  - **Capacidad:** 1000 VA / 600 W interactiva con regulador de voltaje (AVR).
  - **Equipos protegidos en tomas de batería:** PC Central + Monitor de Caja + Router/AP Wi-Fi + Impresora térmica Epson TM-T20III.
  - **Autonomía medida bajo carga real:** **28 minutos** (permite cerrar cuentas en curso y ejecutar backup limpio ante cortes prolongados de energía en Cajamarca).
- **Plan de Contingencia Física:**
  1. **Corte eléctrico < 25 min:** El sistema continúa operando sin interrupción gracias a la UPS que alimenta PC central, router e impresora.
  2. **Caída temporal de Wi-Fi:** Las tablets almacenan las comandas en caché local (Room + Outbox) y reintentan con `client_request_id` idempotente al reconectar.
  3. **Fallo físico de impresora o corte eléctrico > 30 min:** Cocina visualiza las comandas directamente en la pantalla KDS web; si el corte supera la autonomía de la UPS, se activa el talonario físico pre-numerado de contingencia hasta el retorno del fluido eléctrico.
