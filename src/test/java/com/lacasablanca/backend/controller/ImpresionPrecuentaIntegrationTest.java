package com.lacasablanca.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lacasablanca.backend.dto.CobrarRequest;
import com.lacasablanca.backend.dto.CrearPedidoRequest;
import com.lacasablanca.backend.dto.ItemPedidoRequest;
import com.lacasablanca.backend.dto.LoginRequest;
import com.lacasablanca.backend.dto.PagoRequestDto;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImpresionPrecuentaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MesaRepository mesaRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private com.lacasablanca.backend.service.CobroService cobroService;

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
    @DisplayName("PRB-73 / PRB-69: Estado de impresión de pre-cuenta, reimpresión y resiliencia con impresora apagada")
    void estadoImpresionReimpresionYErrorImpresoraSinBloquearComanda() throws Exception {
        // 1. Crear pedido en Mesa 7
        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CrearPedidoRequest(
                                7,
                                List.of(new ItemPedidoRequest(1, 2, "Mesa 7")),
                                "uuid-prb73-mesa7-01"
                        ))))
                .andExpect(status().isCreated());

        // 2. Solicitar pre-cuenta -> genera trabajo de impresión centralizado (ADR-02, sin conexión directa Android)
        mockMvc.perform(post("/api/v1/mesas/7/precuenta")
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesa_id").value(7))
                .andExpect(jsonPath("$.estado_impresion").value("IMPRESO"))
                .andExpect(jsonPath("$.intentos_impresion").value(1));

        // 3. Consultar estado de impresión de la mesa
        mockMvc.perform(get("/api/v1/mesas/7/impresion")
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesa_id").value(7))
                .andExpect(jsonPath("$.estado").value("IMPRESO"))
                .andExpect(jsonPath("$.intentos").value(1));

        // 4. Prueba con impresora apagada (simular_error=true): reporta estado ERROR sin bloquear la comanda
        mockMvc.perform(post("/api/v1/mesas/7/precuenta/reimprimir?simular_error=true")
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesa_id").value(7))
                .andExpect(jsonPath("$.estado").value("ERROR"))
                .andExpect(jsonPath("$.intentos").value(2))
                .andExpect(jsonPath("$.mensaje_error").isNotEmpty());

        // 5. Reintento exitoso cuando la impresora vuelve a estar encendida
        mockMvc.perform(post("/api/v1/mesas/7/precuenta/reimprimir")
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesa_id").value(7))
                .andExpect(jsonPath("$.estado").value("IMPRESO"))
                .andExpect(jsonPath("$.intentos").value(3));

        // 6. El cobro de la mesa no se bloquea en ningún momento
        CobrarRequest cobrarReq = new CobrarRequest(
                List.of(new PagoRequestDto("EFECTIVO", new BigDecimal("100.00"), null)),
                BigDecimal.ZERO,
                "Cobro Mesa 7",
                "uuid-cobro-m7-prb73"
        );
        mockMvc.perform(post("/api/v1/mesas/7/cobrar")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cobrarReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(96.00));
    }

    @Test
    @DisplayName("Precuenta persiste cola de impresión en BD con estado FALLIDO y reconstruye tras limpiar memoria")
    void persistenciaColaImpresionYReconstruccionDesdeDb() throws Exception {
        Mesa mesa = mesaRepository.findById(7).orElseThrow();
        mesa.setEstado(EstadoMesa.OCUPADA);
        mesaRepository.save(mesa);

        // 1. Simular error en reimpresión -> se guarda FALLIDO en BD y retorna ERROR
        mockMvc.perform(post("/api/v1/mesas/7/precuenta/reimprimir?simular_error=true")
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ERROR"));

        // 2. Simular reinicio / limpiar memoria
        cobroService.limpiarCacheParaPruebas();

        // 3. Consultar estado de impresión debe reconstruir desde BD mapeando FALLIDO a ERROR sin crear job falso
        mockMvc.perform(get("/api/v1/mesas/7/impresion")
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesa_id").value(7))
                .andExpect(jsonPath("$.estado").value("ERROR"));
    }
}
