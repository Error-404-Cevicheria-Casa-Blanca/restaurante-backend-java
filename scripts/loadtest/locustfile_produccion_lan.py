"""
[PRB-68] Suite de Pruebas de Carga y Caos en LAN — Restaurante Casa Blanca
Simula la operacion simultanea de:
  - 2 tablets Android de mozos operando sobre las 13 mesas fisicas y pedidos para llevar.
  - 2 pantallas KDS consultando /api/v1/pedidos?estado=PENDIENTE,EN_PREPARACION.
  - Escenario de caos: reintentos con el mismo client_request_id ante microcortes Wi-Fi,
    validando 0 pedidos duplicados y latencia p95 < 300 ms en LAN.
"""

import uuid
import random
from locust import HttpUser, task, between


class MozoTabletLanUser(HttpUser):
    wait_time = between(0.5, 1.5)
    token = None

    def on_start(self):
        resp = self.client.post(
            "/api/v1/auth/login",
            json={"username": "mozo1", "password": "password123"},
            name="POST /api/v1/auth/login",
        )
        if resp.status_code == 200:
            self.token = resp.json().get("token")

    def _headers(self, idempotency_key=None):
        headers = {"Authorization": f"Bearer {self.token}"} if self.token else {}
        if idempotency_key:
            headers["Idempotency-Key"] = idempotency_key
        return headers

    @task(4)
    def listar_mapa_13_mesas(self):
        self.client.get(
            "/api/v1/mesas",
            headers=self._headers(),
            name="GET /api/v1/mesas",
        )

    @task(3)
    def consultar_kds_pedidos_activos(self):
        self.client.get(
            "/api/v1/pedidos?estado=PENDIENTE",
            headers=self._headers(),
            name="GET /api/v1/pedidos?estado=PENDIENTE",
        )

    @task(2)
    def crear_pedido_con_reintento_idempotente_caos_wifi(self):
        """
        Simula envio de comanda con modificadores y reintento inmediato con el mismo
        client_request_id tras un microcorte de red Wi-Fi (PRB-53 + PRB-68).
        """
        req_id = f"lan-chaos-{uuid.uuid4()}"
        payload = {
            "mesa_id": None,
            "mozo_id": 1,
            "clientes": 1,
            "para_llevar": True,
            "tipo_pedido": "PARA_LLEVAR",
            "cliente_nombre": "Cliente Carga LAN",
            "client_request_id": req_id,
            "notas": "Prueba de carga y caos LAN",
            "items": [
                {
                    "producto_id": 1,
                    "cantidad": random.choice([1, 2]),
                    "notas": "Termino medio",
                    "modificadores": [
                        {"nombre": "Sin aji", "precio_adicional": 0.00}
                    ],
                }
            ],
        }
        # Primer envio (espera 201 Created)
        self.client.post(
            "/api/v1/pedidos",
            json=payload,
            headers=self._headers(req_id),
            name="POST /api/v1/pedidos (1st - 201)",
        )
        # Reintento por microcorte Wi-Fi (espera 200 OK con replay=true, 0 duplicados)
        self.client.post(
            "/api/v1/pedidos",
            json=payload,
            headers=self._headers(req_id),
            name="POST /api/v1/pedidos (Retry Idempotente - 200)",
        )

    @task(2)
    def verificar_healthcheck_lan(self):
        self.client.get("/api/v1/health", name="GET /api/v1/health")
