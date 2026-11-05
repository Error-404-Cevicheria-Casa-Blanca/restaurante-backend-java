# Pruebas de Carga y Estrés con Locust (PRB-25)

## 1. Objetivo
Ejecutar pruebas de carga automatizadas contra la API REST del backend de restaurante ("La Casa Blanca"), simulando alta concurrencia de mozos en operación continua, evaluando estabilidad, tiempos de respuesta (P95), rendimiento (RPS) y tasa de fallos.

---

## 2. Escenario de Carga y Flujo del Mozo
El script `locustfile.py` modela el ciclo de vida real de un mozo autenticado:
1. **Autenticación JWT:** `POST /api/v1/auth/login` con credenciales de mozo y persistencia de cabecera `Authorization: Bearer <token>`.
2. **Consulta del Salón:** `GET /api/v1/mesas` con ponderación de consulta frecuente y detección dinámica de mesas disponibles.
3. **Consulta de Carta Marina:** `GET /api/v1/productos` obteniendo el catálogo de platos y precios.
4. **Envío de Comanda:** `POST /api/v1/pedidos` registrando pedidos transaccionales con items marinos sobre mesas libres.
5. **Rotación y Liberación de Mesas:** `PATCH /api/v1/mesas/{id}/estado` simulando desocupación tras cobro para mantener la rotación continua del salón.

---

## 3. Umbrales y Criterios de Calidad (PRB-24 / PRB-25)
* **Usuarios Concurrentes:** Rampa ascendente de 1 a 100 usuarios en 2 minutos.
* **Tiempo de Respuesta (Percentil 95 - P95):**
  * `< 200 ms` para peticiones de lectura (`GET /api/v1/mesas`, `GET /api/v1/productos`).
  * `< 400 ms` para transacciones de escritura (`POST /api/v1/pedidos`, `PATCH /api/v1/mesas/{id}/estado`).
* **Tasa de Error:** `< 1.0%` bajo carga nominal y concurrencia pico.
* **Throughput (RPS):** Capacidad sostenida del backend sin saturación de hilos ni caídas de conexiones JDBC a PostgreSQL.

---

## 4. Instrucciones de Ejecución

### Prerrequisitos
- Python 3.10+ con dependencias instaladas:
  ```bash
  pip install -r load-tests/requirements.txt
  ```
- Backend Spring Boot levantado en `http://localhost:8080`.

### Ejecución Headless y Generación de Reporte HTML
```powershell
python -m locust -f load-tests/locustfile.py --headless `
  -u 100 -r 2 --run-time 2m `
  --host http://localhost:8080 `
  --html load-tests/reporte-locust-prb25.html `
  --csv load-tests/locust_stats
```

### Ejecución con Interfaz Gráfica (Web UI)
```powershell
python -m locust -f load-tests/locustfile.py --host http://localhost:8080
```
Abrir `http://localhost:8089` en el navegador.
