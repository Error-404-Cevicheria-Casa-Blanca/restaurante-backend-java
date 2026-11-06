package com.lacasablanca.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lacasablanca.backend.dto.AnularPedidoRequest;
import com.lacasablanca.backend.dto.CobrarRequest;
import com.lacasablanca.backend.dto.CrearPedidoRequest;
import com.lacasablanca.backend.dto.EstadoMesaRequest;
import com.lacasablanca.backend.dto.EstadoPedidoRequest;
import com.lacasablanca.backend.dto.ItemPedidoRequest;
import com.lacasablanca.backend.dto.LoginRequest;
import com.lacasablanca.backend.dto.PagoRequestDto;
import com.lacasablanca.backend.model.EstadoMesa;
import com.lacasablanca.backend.model.EstadoPedido;
import com.lacasablanca.backend.model.Mesa;
import com.lacasablanca.backend.model.Pedido;
import com.lacasablanca.backend.repository.MesaRepository;
import com.lacasablanca.backend.repository.PedidoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PedidoIdempotenciaAnulacionIntegrationTest {

    private static final int MESA_ID = 7;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MesaRepository mesaRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    private String tokenMozo;

    @BeforeEach
    void setup() throws Exception {
        LoginRequest loginRequest = new LoginRequest("mozo_01", "password123");
        MvcResult resLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(resLogin.getResponse().getContentAsString());
        tokenMozo = json.get("token").asText();

        resetearMesa(MESA_ID);
    }

    @AfterEach
    void tearDown() {
        resetearMesa(MESA_ID);
    }

    private void resetearMesa(int id) {
        List<Pedido> pedidosPrevios = pedidoRepository.findByMesaIdOrderByFechaCreacionDesc(id);
        if (!pedidosPrevios.isEmpty()) {
            pedidoRepository.deleteAll(pedidosPrevios);
        }
        Mesa mesa = mesaRepository.findById(id).orElse(null);
        if (mesa != null) {
            mesa.setEstado(EstadoMesa.LIBRE);
            mesa.setMozo(null);
            mesa.setFechaApertura(null);
            mesa.setFechaCierre(null);
            mesaRepository.save(mesa);
        }
    }

    private JsonNode crearPedidoBase(String clientRequestId) throws Exception {
        EstadoMesaRequest reqApertura = new EstadoMesaRequest(EstadoMesa.ATENDIDA);
        mockMvc.perform(patch("/api/v1/mesas/" + MESA_ID + "/estado")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqApertura)))
                .andExpect(status().isOk());

        List<ItemPedidoRequest> items = List.of(
                new ItemPedidoRequest(2, 2, "Sin ají"),
                new ItemPedidoRequest(3, 1, "Al tiempo")
        );
        CrearPedidoRequest reqPedido = new CrearPedidoRequest(MESA_ID, items, clientRequestId);

        MvcResult result = mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqPedido)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("PRB-53: POST /api/v1/pedidos con client_request_id es idempotente (201 en creación, 200 en replay)")
    void postPedidoConClientRequestIdEsIdempotente() throws Exception {
        String uuid = UUID.randomUUID().toString();
        JsonNode creado = crearPedidoBase(uuid);
        int pedidoId = creado.get("id").asInt();

        // Reintento con el mismo UUID aunque la mesa ya esté OCUPADA: debe responder 200 OK y no duplicar
        List<ItemPedidoRequest> items = List.of(
                new ItemPedidoRequest(2, 2, "Sin ají"),
                new ItemPedidoRequest(3, 1, "Al tiempo")
        );
        CrearPedidoRequest reqReplay = new CrearPedidoRequest(MESA_ID, items, uuid);

        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqReplay)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pedidoId))
                .andExpect(jsonPath("$.replay").value(true))
                .andExpect(jsonPath("$.client_request_id").value(uuid))
                .andExpect(jsonPath("$.total").value(85.00));

        List<Pedido> pedidosEnBd = pedidoRepository.findByMesaIdOrderByFechaCreacionDesc(MESA_ID);
        assertEquals(1, pedidosEnBd.size(), "No debe duplicarse el pedido en base de datos");
    }

    @Test
    @DisplayName("PRB-53: POST /api/v1/pedidos soporta Idempotency-Key por header HTTP")
    void postPedidoConHeaderIdempotencyKeyEsIdempotente() throws Exception {
        String headerKey = UUID.randomUUID().toString();
        List<ItemPedidoRequest> items = List.of(new ItemPedidoRequest(1, 1, "Normal"));
        CrearPedidoRequest req = new CrearPedidoRequest(MESA_ID, items);

        MvcResult r1 = mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .header("Idempotency-Key", headerKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.replay").value(false))
                .andReturn();

        int idOriginal = objectMapper.readTree(r1.getResponse().getContentAsString()).get("id").asInt();

        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .header("Idempotency-Key", headerKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idOriginal))
                .andExpect(jsonPath("$.replay").value(true));
    }

    @Test
    @DisplayName("PRB-53: GET /api/v1/pedidos filtra por mesa_id y estado correctamente")
    void getPedidosConFiltrosPorMesaYEstado() throws Exception {
        JsonNode creado = crearPedidoBase(UUID.randomUUID().toString());
        int pedidoId = creado.get("id").asInt();

        mockMvc.perform(get("/api/v1/pedidos")
                        .param("mesa_id", String.valueOf(MESA_ID))
                        .param("estado", "PENDIENTE")
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(pedidoId));

        mockMvc.perform(get("/api/v1/pedidos")
                        .param("mesa_id", String.valueOf(MESA_ID))
                        .param("estado", "COBRADO")
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/v1/pedidos/" + pedidoId)
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pedidoId))
                .andExpect(jsonPath("$.eventos", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("PRB-53: PATCH /api/v1/pedidos/{id}/estado actualiza estado y registra auditoría de eventos")
    void patchEstadoPedidoRegistraAuditoria() throws Exception {
        JsonNode creado = crearPedidoBase(UUID.randomUUID().toString());
        int pedidoId = creado.get("id").asInt();

        EstadoPedidoRequest reqCocinando = new EstadoPedidoRequest(EstadoPedido.COCINANDO, "Inicia preparación en cocina");
        mockMvc.perform(patch("/api/v1/pedidos/" + pedidoId + "/estado")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqCocinando)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("COCINANDO"))
                .andExpect(jsonPath("$.eventos", hasSize(2)))
                .andExpect(jsonPath("$.eventos[1].estado_anterior").value("PENDIENTE"))
                .andExpect(jsonPath("$.eventos[1].estado_nuevo").value("COCINANDO"));
    }

    @Test
    @DisplayName("PRB-53: POST /api/v1/pedidos/{id}/anular anula comanda con motivo, audita y libera la mesa")
    void anularPedidoConMotivoLiberaMesaYAudita() throws Exception {
        JsonNode creado = crearPedidoBase(UUID.randomUUID().toString());
        int pedidoId = creado.get("id").asInt();

        AnularPedidoRequest reqAnular = new AnularPedidoRequest("Cliente se retiró por emergencia familiar");

        mockMvc.perform(post("/api/v1/pedidos/" + pedidoId + "/anular")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqAnular)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ANULADO"))
                .andExpect(jsonPath("$.motivo_anulacion").value("Cliente se retiró por emergencia familiar"))
                .andExpect(jsonPath("$.eventos", hasSize(2)))
                .andExpect(jsonPath("$.eventos[1].estado_nuevo").value("ANULADO"));

        Mesa mesa = mesaRepository.findById(MESA_ID).orElseThrow();
        assertEquals(EstadoMesa.LIBRE, mesa.getEstado(), "La mesa debe quedar LIBRE al anularse su único pedido");
    }

    @Test
    @DisplayName("PRB-53: POST /api/v1/pedidos/{id}/anular sobre pedido ya COBRADO retorna 409 Conflict")
    void anularPedidoCobradoRetorna409() throws Exception {
        JsonNode creado = crearPedidoBase(UUID.randomUUID().toString());
        int pedidoId = creado.get("id").asInt();

        CobrarRequest reqCobro = new CobrarRequest(
                List.of(new PagoRequestDto("EFECTIVO", BigDecimal.valueOf(85.00), null)),
                BigDecimal.ZERO,
                "Cobro previo",
                UUID.randomUUID().toString()
        );
        mockMvc.perform(post("/api/v1/mesas/" + MESA_ID + "/cobrar")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqCobro)))
                .andExpect(status().isCreated());

        AnularPedidoRequest reqAnular = new AnularPedidoRequest("Intento de anular post-cobro");
        mockMvc.perform(post("/api/v1/pedidos/" + pedidoId + "/anular")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqAnular)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PRB-53: POST /api/v1/pedidos/{id}/items/{itemId}/anular recalcula total D13 y audita")
    void anularItemDePedidoRecalculaTotalYAudita() throws Exception {
        JsonNode creado = crearPedidoBase(UUID.randomUUID().toString());
        int pedidoId = creado.get("id").asInt();
        int itemChichaId = creado.get("items").get(1).get("id").asInt();

        AnularPedidoRequest reqAnularItem = new AnularPedidoRequest("Error de digitación de bebida");

        // Se elimina Chicha (S/ 15.00), quedan 2 Chicharrones (S/ 70.00 -> subtotal 59.32, igv 10.68)
        mockMvc.perform(post("/api/v1/pedidos/" + pedidoId + "/items/" + itemChichaId + "/anular")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqAnularItem)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.total").value(70.00))
                .andExpect(jsonPath("$.subtotal").value(59.32))
                .andExpect(jsonPath("$.igv").value(10.68))
                .andExpect(jsonPath("$.eventos", hasSize(2)));
    }
}
