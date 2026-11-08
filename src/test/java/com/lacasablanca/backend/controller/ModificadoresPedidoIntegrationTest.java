package com.lacasablanca.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lacasablanca.backend.dto.CrearPedidoRequest;
import com.lacasablanca.backend.dto.ItemPedidoRequest;
import com.lacasablanca.backend.dto.LoginRequest;
import com.lacasablanca.backend.dto.ModificadorItemDto;
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

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModificadoresPedidoIntegrationTest {

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
    @DisplayName("PRB-74: Pedido con 2 platos y 2 modificadores guarda modificadores, no altera precio base y los muestra en KDS y pre-cuenta")
    void pedidoCon2PlatosY2ModificadoresSeGuardaYMuestraEnKdsYPreCuenta() throws Exception {
        ItemPedidoRequest plato1 = new ItemPedidoRequest(
                1,
                1,
                "Cliente alérgico a cebolla",
                List.of(
                        new ModificadorItemDto("Sin cebolla"),
                        new ModificadorItemDto("Extra limón")
                )
        );
        ItemPedidoRequest plato2 = new ItemPedidoRequest(
                2,
                1,
                null,
                List.of(
                        new ModificadorItemDto("Salsa tártara aparte"),
                        new ModificadorItemDto("Bien crocante")
                )
        );

        CrearPedidoRequest request = new CrearPedidoRequest(
                1,
                List.of(plato1, plato2),
                "uuid-prb74-2platos-2mods"
        );

        // 1. Crear pedido y verificar respuesta para KDS (items e items_por_estacion)
        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(83.00))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].modificadores", hasSize(2)))
                .andExpect(jsonPath("$.items[0].modificadores[0].nombre").value("Sin cebolla"))
                .andExpect(jsonPath("$.items[0].modificadores[1].nombre").value("Extra limón"))
                .andExpect(jsonPath("$.items[0].notas", containsString("Sin cebolla")))
                .andExpect(jsonPath("$.items[1].modificadores", hasSize(2)))
                .andExpect(jsonPath("$.items[1].modificadores[0].nombre").value("Salsa tártara aparte"))
                .andExpect(jsonPath("$.items[1].modificadores[1].nombre").value("Bien crocante"))
                .andExpect(jsonPath("$.items_por_estacion").isMap());

        // 2. Consultar pre-cuenta de la mesa 1 y verificar que los modificadores se muestran
        mockMvc.perform(get("/api/v1/mesas/1/cuenta")
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(83.00))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].modificadores", hasSize(2)))
                .andExpect(jsonPath("$.items[0].modificadores[0].nombre").value("Sin cebolla"))
                .andExpect(jsonPath("$.items[1].modificadores", hasSize(2)))
                .andExpect(jsonPath("$.items[1].modificadores[0].nombre").value("Salsa tártara aparte"));
    }

    @Test
    @DisplayName("PRB-74: Modificador con precio adicional configurado incrementa el precio unitario y el total")
    void modificadorConPrecioConfiguradoIncrementaTotal() throws Exception {
        ItemPedidoRequest platoConExtra = new ItemPedidoRequest(
                1,
                2,
                "Con guarnición extra",
                List.of(
                        new ModificadorItemDto("Extra camote glaseado", BigDecimal.ZERO),
                        new ModificadorItemDto("Porción extra de pulpo", new BigDecimal("7.00"))
                )
        );

        CrearPedidoRequest request = new CrearPedidoRequest(
                2,
                List.of(platoConExtra),
                "uuid-prb74-mod-con-precio"
        );

        // Precio base 48.00 + 7.00 = 55.00 unitario * 2 = 110.00 total
        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].precio_unitario").value(55.00))
                .andExpect(jsonPath("$.items[0].subtotal").value(110.00))
                .andExpect(jsonPath("$.total").value(110.00));
    }
}
