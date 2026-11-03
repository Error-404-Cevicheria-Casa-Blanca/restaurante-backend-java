package com.lacasablanca.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lacasablanca.backend.dto.CrearPedidoRequest;
import com.lacasablanca.backend.dto.EstadoMesaRequest;
import com.lacasablanca.backend.dto.ItemPedidoRequest;
import com.lacasablanca.backend.dto.LoginRequest;
import com.lacasablanca.backend.model.EstadoMesa;
import com.lacasablanca.backend.model.Mesa;
import com.lacasablanca.backend.model.Pedido;
import com.lacasablanca.backend.model.Producto;
import com.lacasablanca.backend.repository.DetallePedidoRepository;
import com.lacasablanca.backend.repository.MesaRepository;
import com.lacasablanca.backend.repository.PedidoRepository;
import com.lacasablanca.backend.repository.ProductoRepository;
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

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FlujoRestauranteIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MesaRepository mesaRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private DetallePedidoRepository detallePedidoRepository;

    @Autowired
    private ProductoRepository productoRepository;

    private static final int MESA_ID = 5;
    private String tokenMozo;

    @BeforeEach
    void prepararMesaYToken() throws Exception {
        Mesa mesa = mesaRepository.findById(MESA_ID).orElseThrow();
        mesa.setEstado(EstadoMesa.LIBRE);
        mesa.setMozo(null);
        mesa.setFechaApertura(null);
        mesa.setFechaCierre(null);
        mesaRepository.save(mesa);

        LoginRequest login = new LoginRequest("mozo_01", "password123");
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        this.tokenMozo = json.get("token").asText();
    }

    @AfterEach
    void limpiarMesa() {
        Mesa mesa = mesaRepository.findById(MESA_ID).orElse(null);
        if (mesa != null) {
            mesa.setEstado(EstadoMesa.LIBRE);
            mesa.setMozo(null);
            mesa.setFechaApertura(null);
            mesa.setFechaCierre(null);
            mesaRepository.save(mesa);
        }
    }

    @Test
    @DisplayName("PRB-21 E7: Flujo E2E completo (Login -> Mesas -> Apertura -> Catálogo -> Pedido transaccional -> Cuenta -> Cobro y liberación)")
    void flujoCompletoComandaYMesasExitoso() throws Exception {
        String authHeader = "Bearer " + tokenMozo;

        // 1. Consultar mesas y verificar mesa 5 en LIBRE
        mockMvc.perform(get("/api/v1/mesas")
                .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(13)))
                .andExpect(jsonPath("$[4].numero").value(5))
                .andExpect(jsonPath("$[4].estado").value("LIBRE"));

        // 2. Mozo abre mesa 5 (PATCH a ATENDIDA)
        EstadoMesaRequest reqAtendida = new EstadoMesaRequest(EstadoMesa.ATENDIDA);
        mockMvc.perform(patch("/api/v1/mesas/" + MESA_ID + "/estado")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqAtendida)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(MESA_ID))
                .andExpect(jsonPath("$.estado").value("ATENDIDA"));

        Mesa mesaAbierta = mesaRepository.findById(MESA_ID).orElseThrow();
        assertEquals(EstadoMesa.ATENDIDA, mesaAbierta.getEstado());
        assertNotNull(mesaAbierta.getMozo());
        assertEquals("mozo_01", mesaAbierta.getMozo().getUsername());
        assertNotNull(mesaAbierta.getFechaApertura());

        // 3. Consultar catálogo para armar pedido
        mockMvc.perform(get("/api/v1/productos")
                .header("Authorization", authHeader))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)));

        List<Producto> productos = productoRepository.findAll();
        Producto ceviche = productos.stream()
                .filter(p -> p.getNombre().contains("Ceviche"))
                .findFirst()
                .orElseThrow();
        Producto chicha = productos.stream()
                .filter(p -> p.getNombre().contains("Morada"))
                .findFirst()
                .orElseThrow();

        // 4. Crear comanda transaccional (2 Ceviches + 1 Chicha)
        // Precio esperado: 48.00 * 2 + 15.00 * 1 = 111.00
        // Subtotal esperado: 111.00 / 1.18 = 94.07
        // IGV esperado: 111.00 - 94.07 = 16.93
        CrearPedidoRequest pedidoReq = new CrearPedidoRequest(MESA_ID, List.of(
                new ItemPedidoRequest(ceviche.getId(), 2, "sin picante"),
                new ItemPedidoRequest(chicha.getId(), 1, "helada")
        ));

        MvcResult resPedido = mockMvc.perform(post("/api/v1/pedidos")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pedidoReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mesa_id").value(MESA_ID))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.total").value(111.00))
                .andExpect(jsonPath("$.subtotal").value(94.07))
                .andExpect(jsonPath("$.igv").value(16.93))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items_por_estacion.BARRA_FRIA", hasSize(1)))
                .andExpect(jsonPath("$.items_por_estacion.BEBIDAS", hasSize(1)))
                .andReturn();

        JsonNode jsonPedido = objectMapper.readTree(resPedido.getResponse().getContentAsString());
        Integer pedidoId = jsonPedido.get("id").asInt();

        // 5. Verificar que mesa pasó a OCUPADA automáticamente en BD
        Mesa mesaOcupada = mesaRepository.findById(MESA_ID).orElseThrow();
        assertEquals(EstadoMesa.OCUPADA, mesaOcupada.getEstado());

        // 6. Verificar persistencia de Pedido en H2
        Pedido pedidoDb = pedidoRepository.findById(pedidoId).orElseThrow();
        assertEquals(new BigDecimal("111.00"), pedidoDb.getTotal());
        long detallesCount = detallePedidoRepository.findAll().stream()
                .filter(d -> d.getPedido() != null && pedidoId.equals(d.getPedido().getId()))
                .count();
        assertEquals(2, detallesCount);

        // 7. Intentar abrir mesa ya OCUPADA desde otra tablet -> 409 Conflict
        mockMvc.perform(patch("/api/v1/mesas/" + MESA_ID + "/estado")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqAtendida)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("La mesa " + MESA_ID + " no está disponible. Estado actual: OCUPADA"));

        // 8. Cliente solicita la cuenta (PATCH a CUENTA_SOLICITADA)
        EstadoMesaRequest reqCuenta = new EstadoMesaRequest(EstadoMesa.CUENTA_SOLICITADA);
        mockMvc.perform(patch("/api/v1/mesas/" + MESA_ID + "/estado")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqCuenta)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CUENTA_SOLICITADA"));

        // 9. No se permite agregar nuevo pedido a mesa en CUENTA_SOLICITADA -> 409 Conflict
        mockMvc.perform(post("/api/v1/pedidos")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pedidoReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("La mesa " + MESA_ID + " no está disponible. Estado actual: CUENTA_SOLICITADA"));

        // 10. Pago y liberación de mesa (PATCH a LIBRE)
        EstadoMesaRequest reqLibre = new EstadoMesaRequest(EstadoMesa.LIBRE);
        mockMvc.perform(patch("/api/v1/mesas/" + MESA_ID + "/estado")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqLibre)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("LIBRE"));

        Mesa mesaLiberada = mesaRepository.findById(MESA_ID).orElseThrow();
        assertEquals(EstadoMesa.LIBRE, mesaLiberada.getEstado());
        assertNotNull(mesaLiberada.getFechaCierre());
        assertTrue(mesaLiberada.getFechaCierre().isAfter(mesaLiberada.getFechaApertura()) ||
                mesaLiberada.getFechaCierre().isEqual(mesaLiberada.getFechaApertura()));
    }
}
