package com.lacasablanca.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lacasablanca.backend.dto.CrearPedidoRequest;
import com.lacasablanca.backend.dto.EstadoMesaRequest;
import com.lacasablanca.backend.dto.ItemPedidoRequest;
import com.lacasablanca.backend.dto.LoginRequest;
import com.lacasablanca.backend.model.EstadoMesa;
import com.lacasablanca.backend.model.Mesa;
import com.lacasablanca.backend.model.Pedido;
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

import java.util.Collections;
import java.util.List;

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
class PedidoExtensionComandaIntegrationTest {

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
    @DisplayName("PRB-82: Consultar comanda con GET /api/v1/pedidos?mesa_id=X y ampliar mesa OCUPADA sin duplicar pedido")
    void consultarYAmpliarComandaEnMesaOcupadaSinDuplicarPedido() throws Exception {
        // 1. Crear comanda inicial en Mesa 4 (1 Ceviche = S/ 48.00) -> pasa a OCUPADA
        CrearPedidoRequest reqInicial = new CrearPedidoRequest(
                4,
                List.of(new ItemPedidoRequest(1, 1, "Entrada inicial")),
                "uuid-prb82-inicial-m4"
        );

        MvcResult resInicial = mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqInicial)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mesa_id").value(4))
                .andExpect(jsonPath("$.total").value(48.00))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andReturn();

        int pedidoId = objectMapper.readTree(resInicial.getResponse().getContentAsString()).get("id").asInt();

        // 2. Consultar consumo actual de la mesa con GET /api/v1/pedidos?mesa_id=4
        mockMvc.perform(get("/api/v1/pedidos")
                        .param("mesa_id", "4")
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(pedidoId))
                .andExpect(jsonPath("$[0].items", hasSize(1)))
                .andExpect(jsonPath("$[0].total").value(48.00));

        // 3. Agregar platos mediante POST /api/v1/pedidos sobre la mesa 4 ya OCUPADA (1 Chicha = S/ 15.00)
        // No debe duplicar el pedido: mantiene el mismo pedidoId y actualiza el total a S/ 63.00
        CrearPedidoRequest reqAmpliacion = new CrearPedidoRequest(
                4,
                List.of(new ItemPedidoRequest(3, 1, "Bebida adicional")),
                "uuid-prb82-ampliacion-m4"
        );

        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqAmpliacion)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(pedidoId))
                .andExpect(jsonPath("$.mesa_id").value(4))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.total").value(63.00));

        // 4. También soporta POST /api/v1/pedidos/{id}/items para ampliar directamente (1 Chicharrón = S/ 35.00 -> Total S/ 98.00)
        CrearPedidoRequest reqItemsDirecto = new CrearPedidoRequest(
                4,
                List.of(new ItemPedidoRequest(2, 1, "Plato extra")),
                "uuid-prb82-items-directo-m4"
        );

        mockMvc.perform(post("/api/v1/pedidos/" + pedidoId + "/items")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqItemsDirecto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(pedidoId))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.total").value(98.00));

        // Verificar que sigue existiendo exactamente 1 comanda en la base de datos para la mesa 4
        List<Pedido> pedidosMesa4 = pedidoRepository.findByMesaIdOrderByFechaCreacionDesc(4);
        assertEquals(1, pedidosMesa4.size());
    }

    @Test
    @DisplayName("PRB-82: Validaciones 400 y 409 al ampliar comanda (lista vacía -> 400, mesa en CUENTA_SOLICITADA -> 409)")
    void validaciones400Y409AlAmpliarComanda() throws Exception {
        // 1. Crear comanda en Mesa 6
        MvcResult res = mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CrearPedidoRequest(
                                6,
                                List.of(new ItemPedidoRequest(1, 1, null)),
                                "uuid-prb82-m6-01"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        int pedidoId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asInt();

        // 2. Ampliar con lista vacía -> 400 Bad Request
        mockMvc.perform(post("/api/v1/pedidos/" + pedidoId + "/items")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CrearPedidoRequest(
                                6, Collections.emptyList(), "uuid-prb82-empty"
                        ))))
                .andExpect(status().isBadRequest());

        // 3. Pasar Mesa 6 a CUENTA_SOLICITADA -> intentar ampliar retorna 409 Conflict
        mockMvc.perform(patch("/api/v1/mesas/6/estado")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EstadoMesaRequest(EstadoMesa.CUENTA_SOLICITADA))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/pedidos/" + pedidoId + "/items")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CrearPedidoRequest(
                                6, List.of(new ItemPedidoRequest(3, 1, null)), "uuid-prb82-conflict"
                        ))))
                .andExpect(status().isConflict());
    }
}
