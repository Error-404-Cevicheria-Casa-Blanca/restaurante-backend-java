package com.lacasablanca.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lacasablanca.backend.dto.CrearPedidoRequest;
import com.lacasablanca.backend.dto.DividirMesaRequest;
import com.lacasablanca.backend.dto.FusionarMesasRequest;
import com.lacasablanca.backend.dto.ItemPedidoRequest;
import com.lacasablanca.backend.dto.LoginRequest;
import com.lacasablanca.backend.dto.TransferirMesaRequest;
import com.lacasablanca.backend.model.EstadoMesa;
import com.lacasablanca.backend.model.Mesa;
import com.lacasablanca.backend.repository.MesaRepository;
import com.lacasablanca.backend.repository.PedidoRepository;
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

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MesaDivisionFusionTransferenciaIntegrationTest {

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
    void setUp() throws Exception {
        pedidoRepository.deleteAll();
        for (Mesa m : mesaRepository.findAll()) {
            m.setEstado(EstadoMesa.LIBRE);
            m.setMozo(null);
            m.setFechaApertura(null);
            m.setFechaCierre(null);
            mesaRepository.save(m);
        }

        LoginRequest loginReq = new LoginRequest("mozo_01", "password123");
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(res.getResponse().getContentAsString());
        tokenMozo = node.get("token").asText();
    }

    @Test
    @DisplayName("PRB-75: Dividir una mesa genera dos cuentas independientes y cuadradas sin duplicar ítems")
    void dividirMesaGeneraDosCuentasIndependientesYCuadradas() throws Exception {
        CrearPedidoRequest crearReq = new CrearPedidoRequest(
                1,
                List.of(
                        new ItemPedidoRequest(1, 1, "Ceviche"),
                        new ItemPedidoRequest(2, 1, "Chicharron")
                ),
                "uuid-prb75-dividir-001"
        );

        MvcResult resCrear = mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(crearReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(83.00))
                .andReturn();

        JsonNode jsonPedido = objectMapper.readTree(resCrear.getResponse().getContentAsString());
        int item2Id = jsonPedido.get("items").get(1).get("id").asInt();

        DividirMesaRequest divReq = new DividirMesaRequest(List.of(item2Id), 2);

        mockMvc.perform(post("/api/v1/mesas/1/dividir")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(divReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesa_origen_id").value(1))
                .andExpect(jsonPath("$.mesa_destino_id").value(2))
                .andExpect(jsonPath("$.pedido_origen.total").value(48.00))
                .andExpect(jsonPath("$.pedido_origen.items", hasSize(1)))
                .andExpect(jsonPath("$.pedido_nuevo.total").value(35.00))
                .andExpect(jsonPath("$.pedido_nuevo.items", hasSize(1)))
                .andExpect(jsonPath("$.total_combinado").value(83.00));

        // Verificar cuentas independientes en ambas mesas
        mockMvc.perform(get("/api/v1/mesas/1/cuenta")
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(48.00))
                .andExpect(jsonPath("$.items", hasSize(1)));

        mockMvc.perform(get("/api/v1/mesas/2/cuenta")
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(35.00))
                .andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    @DisplayName("PRB-75: Fusionar dos mesas une sus pedidos sin duplicados y libera la mesa origen")
    void fusionarDosMesasUnePedidosSinDuplicados() throws Exception {
        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CrearPedidoRequest(
                                3,
                                List.of(new ItemPedidoRequest(1, 1, null)),
                                "uuid-prb75-fusion-m3"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CrearPedidoRequest(
                                4,
                                List.of(new ItemPedidoRequest(3, 1, null)),
                                "uuid-prb75-fusion-m4"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/mesas/3/fusionar")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FusionarMesasRequest(4))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesa_id").value(3))
                .andExpect(jsonPath("$.total").value(63.00))
                .andExpect(jsonPath("$.pedidos", hasSize(2)))
                .andExpect(jsonPath("$.items", hasSize(2)));

        Mesa mesa4 = mesaRepository.findById(4).orElseThrow();
        assertEquals(EstadoMesa.LIBRE, mesa4.getEstado());
    }

    @Test
    @DisplayName("PRB-75: Transferir mueve el pedido a otra mesa libre sin perder trazabilidad")
    void transferirMesaMuevePedidoSinPerderTrazabilidad() throws Exception {
        MvcResult resCrear = mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CrearPedidoRequest(
                                5,
                                List.of(new ItemPedidoRequest(1, 1, "Transferir")),
                                "uuid-prb75-transfer-m5"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        int pedidoId = objectMapper.readTree(resCrear.getResponse().getContentAsString()).get("id").asInt();

        mockMvc.perform(post("/api/v1/mesas/5/transferir")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransferirMesaRequest(6))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesa_id").value(6))
                .andExpect(jsonPath("$.total").value(48.00));

        Mesa mesa5 = mesaRepository.findById(5).orElseThrow();
        Mesa mesa6 = mesaRepository.findById(6).orElseThrow();
        assertEquals(EstadoMesa.LIBRE, mesa5.getEstado());
        assertEquals(EstadoMesa.OCUPADA, mesa6.getEstado());

        // Verificar trazabilidad en eventos del pedido
        mockMvc.perform(get("/api/v1/pedidos/" + pedidoId)
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesa_id").value(6))
                .andExpect(jsonPath("$.eventos[1].motivo", containsString("Transferencia de mesa 5 a mesa 6")));
    }

    @Test
    @DisplayName("PRB-75: Estados inválidos en dividir, fusionar y transferir responden 409 Conflict")
    void estadosInvalidosResponden409() throws Exception {
        // 1. Dividir una mesa LIBRE -> 409
        mockMvc.perform(post("/api/v1/mesas/7/dividir")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DividirMesaRequest(List.of(1), 8))))
                .andExpect(status().isConflict());

        // 2. Fusionar cuando una mesa está LIBRE -> 409
        mockMvc.perform(post("/api/v1/mesas/7/fusionar")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FusionarMesasRequest(8))))
                .andExpect(status().isConflict());

        // 3. Transferir hacia una mesa ya OCUPADA -> 409
        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CrearPedidoRequest(
                                9,
                                List.of(new ItemPedidoRequest(1, 1, null)),
                                "uuid-prb75-m9"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CrearPedidoRequest(
                                10,
                                List.of(new ItemPedidoRequest(2, 1, null)),
                                "uuid-prb75-m10"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/mesas/9/transferir")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TransferirMesaRequest(10))))
                .andExpect(status().isConflict());
    }
}
