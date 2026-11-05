import random
from locust import HttpUser, task, between
import urllib3

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

class MozoUser(HttpUser):
    """
    Simulación de usuario Mozo para pruebas de carga y estrés (PRB-25).
    Flujo realista y concurrente:
      1. Login y obtención de token JWT (/api/v1/auth/login)
      2. Consulta de mesas y detección de mesas disponibles (/api/v1/mesas)
      3. Consulta del menú y carta de platos (/api/v1/productos)
      4. Registro de comandas sobre mesas disponibles (/api/v1/pedidos)
      5. Ciclo de rotación de mesas (LIBRE <-> ATENDIDA / LIBRE tras consumo)
    """
    wait_time = between(0.3, 1.0)
    token = None
    headers = {}
    productos_disponibles = [1, 2, 3, 4, 5]
    mesas_libres = list(range(1, 14))

    def on_start(self):
        """Autenticación del mozo antes de iniciar las tareas."""
        credenciales = {
            "usuario": "mozo_01",
            "clave": "password123"
        }
        with self.client.post(
            "/api/v1/auth/login",
            json=credenciales,
            catch_response=True,
            name="[Auth] POST /api/v1/auth/login"
        ) as response:
            if response.status_code == 200:
                data = response.json()
                self.token = data.get("token")
                self.headers = {
                    "Authorization": f"Bearer {self.token}",
                    "Content-Type": "application/json"
                }
                response.success()
            else:
                response.failure(f"Error de login: {response.status_code} - {response.text}")

    @task(6)
    def consultar_mesas(self):
        """Consulta frecuente del estado del salón y actualización de mesas libres."""
        if not self.token:
            return
        with self.client.get(
            "/api/v1/mesas",
            headers=self.headers,
            catch_response=True,
            name="[Mesas] GET /api/v1/mesas"
        ) as response:
            if response.status_code == 200:
                data = response.json()
                libres = [m["id"] for m in data if m.get("estado") in ("LIBRE", "ATENDIDA")]
                if libres:
                    self.mesas_libres = libres
                response.success()
            else:
                response.failure(f"Error en GET mesas: {response.status_code}")

    @task(5)
    def consultar_carta(self):
        """Consulta de productos y precios de la carta marina."""
        if not self.token:
            return
        self.client.get(
            "/api/v1/productos",
            headers=self.headers,
            name="[Catalogo] GET /api/v1/productos"
        )

    @task(4)
    def registrar_pedido(self):
        """Envío de comanda sobre una mesa disponible."""
        if not self.token:
            return

        mesa_id = random.choice(self.mesas_libres) if self.mesas_libres else random.randint(1, 13)
        prod_id = random.choice(self.productos_disponibles)
        cant = random.randint(1, 3)

        payload = {
            "mesa_id": mesa_id,
            "items": [
                {
                    "producto_id": prod_id,
                    "cantidad": cant,
                    "notas": "Pedido test Locust"
                }
            ]
        }
        with self.client.post(
            "/api/v1/pedidos",
            json=payload,
            headers=self.headers,
            catch_response=True,
            name="[Pedidos] POST /api/v1/pedidos"
        ) as response:
            if response.status_code == 201:
                response.success()
            elif response.status_code == 409:
                # 409 es el comportamiento esperado del bloqueo concurrente si dos mozos eligen la misma mesa
                response.success()
            else:
                response.failure(f"Fallo en pedido: {response.status_code} - {response.text}")

    @task(2)
    def liberar_mesa_rotacion(self):
        """Simula la finalización de atención y rotación liberando una mesa."""
        if not self.token:
            return
        mesa_id = random.randint(1, 13)
        with self.client.patch(
            f"/api/v1/mesas/{mesa_id}/estado",
            json={"estado": "LIBRE"},
            headers=self.headers,
            catch_response=True,
            name="[Mesas] PATCH /api/v1/mesas/{id}/estado (Rotación)"
        ) as response:
            if response.status_code in (200, 409):
                response.success()
            else:
                response.failure(f"Error rotando mesa: {response.status_code}")
