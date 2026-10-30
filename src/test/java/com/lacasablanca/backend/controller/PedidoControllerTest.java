package com.lacasablanca.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lacasablanca.backend.dto.CrearPedidoRequest;
import com.lacasablanca.backend.dto.ItemPedidoRequest;
import com.lacasablanca.backend.dto.LoginRequest;
import com.lacasablanca.backend.repository.ProductoRepository;
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

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PedidoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductoRepository productoRepository;

    private String tokenMozo;
    private Integer productoValidoId;

    @BeforeEach
    void setup() throws Exception {
        LoginRequest req = new LoginRequest("mozo_01", "password123");
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        this.tokenMozo = json.get("token").asText();

        this.productoValidoId = productoRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getDisponible()))
                .findFirst().map(p -> p.getId()).orElse(1);
    }

    @Test
    @DisplayName("PE-12 E1: POST /api/v1/pedidos con datos válidos retorna HTTP 201 Created y total calculado")
    void crearPedidoExitosoRetorna201() throws Exception {
        CrearPedidoRequest req = new CrearPedidoRequest(2, List.of(
                new ItemPedidoRequest(productoValidoId, 2, "sin picante")
        ));

        mockMvc.perform(post("/api/v1/pedidos")
                .header("Authorization", "Bearer " + tokenMozo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.mesa_id").value(2))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    @DisplayName("PE-12 E2: POST /api/v1/pedidos con lista de items vacía retorna HTTP 400 Bad Request")
    void crearPedidoItemsVacioRetorna400() throws Exception {
        CrearPedidoRequest req = new CrearPedidoRequest(3, Collections.emptyList());

        mockMvc.perform(post("/api/v1/pedidos")
                .header("Authorization", "Bearer " + tokenMozo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    @DisplayName("PE-12 E2: POST /api/v1/pedidos con cantidad menor a 1 retorna HTTP 400 Bad Request")
    void crearPedidoCantidadInvalidaRetorna400() throws Exception {
        CrearPedidoRequest req = new CrearPedidoRequest(3, List.of(
                new ItemPedidoRequest(productoValidoId, 0, "nota")
        ));

        mockMvc.perform(post("/api/v1/pedidos")
                .header("Authorization", "Bearer " + tokenMozo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    @DisplayName("PE-12 D6: GET /api/v1/categorias retorna HTTP 200 y las 4 categorías")
    void listarCategoriasExitoso() throws Exception {
        mockMvc.perform(get("/api/v1/categorias")
                .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(4))));
    }

    @Test
    @DisplayName("PE-12 D6: GET /api/v1/productos retorna HTTP 200 y lista de productos con precio e imagen")
    void listarProductosExitoso() throws Exception {
        mockMvc.perform(get("/api/v1/productos")
                .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].precio").isNumber())
                .andExpect(jsonPath("$[0].categoria_id").isNumber());
    }
}
