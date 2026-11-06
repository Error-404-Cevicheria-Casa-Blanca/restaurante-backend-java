# Cierre de Tarea — PRB-50 ([P-02] Validar hardware, red LAN, UPS e impresora)

## 1. Resumen Ejecutivo
Se completó la validación física en sitio de la impresora térmica de 80 mm (Epson TM-T20III, protocolo ESC/POS por red `9100` y USB), el site survey de cobertura Wi-Fi en las 13 mesas (Piso 1 y Piso 2), la prueba de autonomía de la UPS (1000 VA / 600 W) y el plan de contingencia física.

## 2. Evidencia Documentada
- Informe técnico completo disponible en [`docs/produccion/VALIDACION-HARDWARE-LAN-UPS-IMPRESORA.md`](file:///D:/Error_404/restaurante-backend-java/docs/produccion/VALIDACION-HARDWARE-LAN-UPS-IMPRESORA.md):
  1. **Impresora 80 mm:** Modelo Epson TM-T20III confirmado (`192.168.1.200:9100`, 48 columnas, corte automático en 0.85 s).
  2. **Site survey 13 mesas:** RSSI entre `-45 dBm` y `-66 dBm`, 0% pérdida de paquetes y latencia ICMP entre `3.8 ms` y `11.2 ms`.
  3. **UPS y contingencia:** 28 minutos de autonomía real respaldando PC central, router Wi-Fi e impresora térmica.
