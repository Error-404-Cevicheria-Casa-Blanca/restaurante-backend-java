package com.lacasablanca.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lacasablanca.backend.dto.CrearPedidoRequest;
import com.lacasablanca.backend.dto.ItemPedidoRequest;
import com.lacasablanca.backend.dto.LoginRequest;
import com.lacasablanca.backend.model.EstadoMesa;
import com.lacasablanca.backend.model.Mesa;
import com.lacasablanca.backend.repository.MesaRepository;
import com.lacasablanca.backend.repository.PedidoRepository;
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

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Autowired
    private MesaRepository mesaRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    private String tokenMozo;
    private Integer productoValidoId;
    private Integer productoAgotadoId;

    @BeforeEach
    void setup() throws Exception {
        // Limpieza y reseteo de mesas para aislamiento total de pruebas
        pedidoRepository.deleteAll();
        for (Mesa m : mesaRepository.findAll()) {
            m.setEstado(EstadoMesa.LIBRE);
            mesaRepository.save(m);
        }

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

        this.productoAgotadoId = productoRepository.findAll().stream()
                .filter(p -> Boolean.FALSE.equals(p.getDisponible()))
                .findFirst().map(p -> p.getId()).orElse(null);
    }

    @Test
    @DisplayName("PRB-20 E1: POST /api/v1/pedidos retorna 201, desglose IGV D13 y pasa mesa a OCUPADA")
    void crearPedidoExitosoRetorna201YCambiaMesa() throws Exception {
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
                .andExpect(jsonPath("$.subtotal").isNumber())
                .andExpect(jsonPath("$.igv").isNumber())
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items_por_estacion").isMap());

        // Comprobar estado OCUPADA de la mesa directamente en base de datos
        Mesa mesa2 = mesaRepository.findById(2).orElseThrow();
        assertEquals(EstadoMesa.OCUPADA, mesa2.getEstado());
    }

    @Test
    @DisplayName("PRB-20 E2: POST /api/v1/pedidos con lista vacía retorna HTTP 400 Bad Request")
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
    @DisplayName("PRB-20 E2: POST /api/v1/pedidos con cantidad menor a 1 retorna HTTP 400 Bad Request")
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
    @DisplayName("PRB-20 Validación 409: Mesa ocupada retorna HTTP 409 Conflict")
    void crearPedidoMesaOcupadaRetorna409() throws Exception {
        Mesa m = mesaRepository.findById(3).orElseThrow();
        m.setEstado(EstadoMesa.OCUPADA);
        mesaRepository.save(m);

        CrearPedidoRequest req = new CrearPedidoRequest(3, List.of(
                new ItemPedidoRequest(productoValidoId, 1, null)
        ));

        mockMvc.perform(post("/api/v1/pedidos")
                .header("Authorization", "Bearer " + tokenMozo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", containsString("no está disponible")));
    }

    @Test
    @DisplayName("PRB-20 Validación 409: Producto agotado retorna HTTP 409 Conflict")
    void crearPedidoProductoAgotadoRetorna409() throws Exception {
        if (productoAgotadoId == null) return;

        CrearPedidoRequest req = new CrearPedidoRequest(5, List.of(
                new ItemPedidoRequest(productoAgotadoId, 1, null)
        ));

        mockMvc.perform(post("/api/v1/pedidos")
                .header("Authorization", "Bearer " + tokenMozo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje", containsString("no está disponible")));
    }

    @Test
    @DisplayName("PRB-20 Validación 404: Producto inexistente retorna HTTP 404 Not Found")
    void crearPedidoProductoInexistenteRetorna404() throws Exception {
        CrearPedidoRequest req = new CrearPedidoRequest(5, List.of(
                new ItemPedidoRequest(9999, 1, null)
        ));

        mockMvc.perform(post("/api/v1/pedidos")
                .header("Authorization", "Bearer " + tokenMozo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje", containsString("Producto no encontrado")));
    }

    @Test
    @DisplayName("PRB-20 Seguridad: GET /api/v1/categorias sin token retorna HTTP 401 Unauthorized")
    void categoriasSinTokenRetorna401() throws Exception {
        mockMvc.perform(get("/api/v1/categorias"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    @DisplayName("PRB-20 Seguridad: GET /api/v1/productos sin token retorna HTTP 401 Unauthorized")
    void productosSinTokenRetorna401() throws Exception {
        mockMvc.perform(get("/api/v1/productos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").exists());
    }
}
