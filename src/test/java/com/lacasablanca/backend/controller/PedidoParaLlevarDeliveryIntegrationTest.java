package com.lacasablanca.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lacasablanca.backend.dto.CobrarRequest;
import com.lacasablanca.backend.dto.CrearPedidoRequest;
import com.lacasablanca.backend.dto.EstadoPedidoRequest;
import com.lacasablanca.backend.dto.ItemPedidoRequest;
import com.lacasablanca.backend.dto.LoginRequest;
import com.lacasablanca.backend.dto.PagoRequestDto;
import com.lacasablanca.backend.model.EstadoMesa;
import com.lacasablanca.backend.model.EstadoPedido;
import com.lacasablanca.backend.model.Mesa;
import com.lacasablanca.backend.model.Pago;
import com.lacasablanca.backend.repository.MesaRepository;
import com.lacasablanca.backend.repository.PagoRepository;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PedidoParaLlevarDeliveryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MesaRepository mesaRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private PagoRepository pagoRepository;

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
    @DisplayName("PRB-79: Flujo E2E de pedido Para Llevar sin mesa (registro, KDS, estados y cobro sin afectar salón)")
    void flujoCompletoPedidoParaLlevarSinMesa() throws Exception {
        CrearPedidoRequest reqParaLlevar = new CrearPedidoRequest(
                null,
                List.of(
                        new ItemPedidoRequest(1, 1, "Empaque térmico"),
                        new ItemPedidoRequest(3, 1, "En botella sellada")
                ),
                "uuid-prb79-parallevar-001",
                "PARA_LLEVAR",
                "Carlos Mendoza"
        );

        // 1. Registrar pedido sin mesa_id -> 201 Created, mesa_id=null, para_llevar=true, items_por_estacion para KDS
        MvcResult resCrear = mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqParaLlevar)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mesa_id").value(nullValue()))
                .andExpect(jsonPath("$.para_llevar").value(true))
                .andExpect(jsonPath("$.tipo_pedido").value("PARA_LLEVAR"))
                .andExpect(jsonPath("$.cliente_nombre").value("Carlos Mendoza"))
                .andExpect(jsonPath("$.total").value(63.00))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items_por_estacion").isMap())
                .andReturn();

        int pedidoId = objectMapper.readTree(resCrear.getResponse().getContentAsString()).get("id").asInt();

        // 2. Verificar que ninguna de las 13 mesas del salón cambió de estado
        for (Mesa m : mesaRepository.findAll()) {
            assertEquals(EstadoMesa.LIBRE, m.getEstado());
        }

        // 3. Transicionar en cocina (LISTO) y entrega (SERVIDO)
        mockMvc.perform(patch("/api/v1/pedidos/" + pedidoId + "/estado")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new EstadoPedidoRequest(EstadoPedido.LISTO, "Empacado en cocina")
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("LISTO"));

        // 4. Cobrar pedido sin mesa en POST /api/v1/pedidos/{id}/cobrar
        CobrarRequest cobrarReq = new CobrarRequest(
                List.of(new PagoRequestDto("YAPE", new BigDecimal("63.00"), "OP-YAPE-LLEVAR-01")),
                BigDecimal.ZERO,
                "Cobro pedido para llevar",
                "uuid-cobro-parallevar-001"
        );

        mockMvc.perform(post("/api/v1/pedidos/" + pedidoId + "/cobrar")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cobrarReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(63.00))
                .andExpect(jsonPath("$.pagado").value(63.00))
                .andExpect(jsonPath("$.pedidos[0].estado").value("COBRADO"));

        List<Pago> pagos = pagoRepository.findByPedidoIdOrderByFechaPagoAsc(pedidoId);
        assertEquals(1, pagos.size());
        assertNull(pagos.get(0).getMesa());
        assertEquals("YAPE", pagos.get(0).getMetodo());
    }

    @Test
    @DisplayName("PRB-79: Pedido Delivery sin mesa coexiste con pedidos de salón sin romper su flujo")
    void pedidoDeliveryCoexisteConFlujoDeSalon() throws Exception {
        // Pedido en mesa 1
        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CrearPedidoRequest(
                                1,
                                List.of(new ItemPedidoRequest(1, 1, null)),
                                "uuid-prb79-salon-m1"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mesa_id").value(1))
                .andExpect(jsonPath("$.para_llevar").value(false))
                .andExpect(jsonPath("$.tipo_pedido").value("SALON"));

        // Pedido Delivery sin mesa
        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CrearPedidoRequest(
                                null,
                                List.of(new ItemPedidoRequest(2, 1, "Delivery")),
                                "uuid-prb79-delivery-01",
                                "DELIVERY",
                                "Ana Torres - Jr. Comercio 450"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mesa_id").value(nullValue()))
                .andExpect(jsonPath("$.para_llevar").value(true))
                .andExpect(jsonPath("$.tipo_pedido").value("DELIVERY"));

        // GET /api/v1/pedidos lista ambos pedidos
        mockMvc.perform(get("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }
}
