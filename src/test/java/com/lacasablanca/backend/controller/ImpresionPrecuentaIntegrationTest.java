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
import com.lacasablanca.backend.model.TrabajoImpresion;
import com.lacasablanca.backend.repository.MesaRepository;
import com.lacasablanca.backend.repository.PedidoRepository;
import com.lacasablanca.backend.repository.TrabajoImpresionRepository;
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
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private TrabajoImpresionRepository trabajoImpresionRepository;

    @Autowired
    private com.lacasablanca.backend.service.CobroService cobroService;

    private String tokenMozo;

    @BeforeEach
    void setUp() throws Exception {
        trabajoImpresionRepository.deleteAll();
        pedidoRepository.deleteAll();
        cobroService.limpiarCacheParaPruebas();
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
    @DisplayName("PRB-106 / ADR-01 / ADR-07: Encola COMANDA por estación y PRECUENTA con mesa_id en PENDIENTE, reflejando estado real de impresion_cola")
    void estadoImpresionReimpresionYErrorImpresoraSinBloquearComanda() throws Exception {
        // 1. Crear pedido en Mesa 7 con platos de 2 estaciones (Producto 1: BARRA_FRIA, Producto 3: BEBIDAS)
        MvcResult pedidoRes = mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CrearPedidoRequest(
                                7,
                                List.of(
                                        new ItemPedidoRequest(1, 2, "Mesa 7 ceviche"),
                                        new ItemPedidoRequest(3, 1, "Mesa 7 bebida")
                                ),
                                "uuid-prb106-mesa7-01"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        int pedidoId = objectMapper.readTree(pedidoRes.getResponse().getContentAsString()).get("id").asInt();

        // Verificar que se encolaron las 2 comandas por estación en impresion_cola con estado=PENDIENTE y payload vacío
        List<TrabajoImpresion> comandas = trabajoImpresionRepository.findByPedidoIdOrderByFechaCreacionDesc(pedidoId)
                .stream()
                .filter(ti -> "COMANDA".equals(ti.getTipo()))
                .collect(Collectors.toList());
        assertEquals(2, comandas.size());
        Set<String> estacionesEncoladas = comandas.stream().map(TrabajoImpresion::getEstacion).collect(Collectors.toSet());
        assertTrue(estacionesEncoladas.contains("BARRA_FRIA"));
        assertTrue(estacionesEncoladas.contains("BEBIDAS"));
        for (TrabajoImpresion cmd : comandas) {
            assertEquals("PENDIENTE", cmd.getEstado());
            assertEquals("", cmd.getPayload());
            assertEquals(7, cmd.getMesaId());
            assertEquals(0, cmd.getIntentos());
        }

        // 2. Solicitar pre-cuenta -> encola trabajo PRECUENTA con estado=PENDIENTE y mesa_id=7 (sin simular IMPRESO)
        mockMvc.perform(post("/api/v1/mesas/7/precuenta")
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesa_id").value(7))
                .andExpect(jsonPath("$.estado_impresion").value("PENDIENTE"))
                .andExpect(jsonPath("$.intentos_impresion").value(0));

        // 3. Consultar estado de impresión de la mesa -> devuelve PENDIENTE desde impresion_cola
        mockMvc.perform(get("/api/v1/mesas/7/impresion")
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesa_id").value(7))
                .andExpect(jsonPath("$.tipo").value("PRECUENTA"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.intentos").value(0));

        // 4. Simular que el worker de Django procesa la precuenta y la marca IMPRESO en impresion_cola
        List<TrabajoImpresion> precuentas = trabajoImpresionRepository.findByTipoOrderByFechaCreacionDesc("PRECUENTA")
                .stream()
                .filter(ti -> Integer.valueOf(7).equals(ti.getMesaId()))
                .collect(Collectors.toList());
        assertEquals(1, precuentas.size());
        TrabajoImpresion jobPrecuenta = precuentas.get(0);
        assertEquals(7, jobPrecuenta.getMesaId());
        assertNotNull(jobPrecuenta.getPedido());
        assertEquals(pedidoId, jobPrecuenta.getPedido().getId());
        jobPrecuenta.setEstado("IMPRESO");
        jobPrecuenta.setIntentos(1);
        trabajoImpresionRepository.save(jobPrecuenta);

        mockMvc.perform(get("/api/v1/mesas/7/impresion")
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesa_id").value(7))
                .andExpect(jsonPath("$.estado").value("IMPRESO"))
                .andExpect(jsonPath("$.intentos").value(1));

        // 5. Prueba con impresora apagada (simular_error=true): reporta estado real FALLIDO sin bloquear la comanda
        mockMvc.perform(post("/api/v1/mesas/7/precuenta/reimprimir?simular_error=true")
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesa_id").value(7))
                .andExpect(jsonPath("$.estado").value("FALLIDO"))
                .andExpect(jsonPath("$.intentos").value(1))
                .andExpect(jsonPath("$.mensaje_error").isNotEmpty());

        // 6. Reintento de reimpresión -> encola nuevo trabajo en PENDIENTE
        mockMvc.perform(post("/api/v1/mesas/7/precuenta/reimprimir")
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesa_id").value(7))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.intentos").value(0));

        // 7. El cobro de la mesa no se bloquea en ningún momento
        CobrarRequest cobrarReq = new CobrarRequest(
                List.of(new PagoRequestDto("EFECTIVO", new BigDecimal("150.00"), null)),
                BigDecimal.ZERO,
                "Cobro Mesa 7",
                "uuid-cobro-m7-prb106"
        );
        mockMvc.perform(post("/api/v1/mesas/7/cobrar")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cobrarReq)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PRB-106: Precuenta persiste cola de impresión en BD con estado FALLIDO y reconstruye tras limpiar memoria")
    void persistenciaColaImpresionYReconstruccionDesdeDb() throws Exception {
        Mesa mesa = mesaRepository.findById(7).orElseThrow();
        mesa.setEstado(EstadoMesa.OCUPADA);
        mesaRepository.save(mesa);

        // 1. Simular error en reimpresión -> se guarda FALLIDO en BD con mesa_id=7
        mockMvc.perform(post("/api/v1/mesas/7/precuenta/reimprimir?simular_error=true")
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FALLIDO"));

        // 2. Simular reinicio / limpiar memoria
        cobroService.limpiarCacheParaPruebas();

        // 3. Consultar estado de impresión debe reconstruir desde BD devolviendo estado real FALLIDO
        mockMvc.perform(get("/api/v1/mesas/7/impresion")
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesa_id").value(7))
                .andExpect(jsonPath("$.estado").value("FALLIDO"));
    }

    @Test
    @DisplayName("PRB-106: Ampliar comanda existente con múltiples filas COMANDA previas por estación (reimpresión Django) actualiza la más reciente sin IncorrectResultSizeDataAccessException")
    void ampliarComandaConMultiplesFilasPreviasPorEstacionNoLanzaExcepcion() throws Exception {
        MvcResult pedidoRes = mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CrearPedidoRequest(
                                8,
                                List.of(new ItemPedidoRequest(1, 1, "Ceviche inicial")),
                                "uuid-prb106-mesa8-dup"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        int pedidoId = objectMapper.readTree(pedidoRes.getResponse().getContentAsString()).get("id").asInt();

        // Simular que el worker imprimió la primera comanda y que desde el panel Django se disparó una 2da fila COMANDA para BARRA_FRIA
        List<TrabajoImpresion> iniciales = trabajoImpresionRepository
                .findByPedidoIdAndTipoAndEstacionOrderByFechaCreacionDescIdDesc(pedidoId, "COMANDA", "BARRA_FRIA");
        assertEquals(1, iniciales.size());
        TrabajoImpresion primera = iniciales.get(0);
        primera.setEstado("IMPRESO");
        primera.setIntentos(1);
        trabajoImpresionRepository.save(primera);

        TrabajoImpresion segundaReimpresa = new TrabajoImpresion(
                primera.getPedido(), 8, "COMANDA", "BARRA_FRIA", "Reimpresion Django", "IMPRESO"
        );
        segundaReimpresa.setIntentos(1);
        trabajoImpresionRepository.save(segundaReimpresa);

        // Ampliar el pedido agregando otro ítem de BARRA_FRIA y uno de BEBIDAS
        mockMvc.perform(post("/api/v1/pedidos/" + pedidoId + "/items")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CrearPedidoRequest(
                                8,
                                List.of(
                                        new ItemPedidoRequest(1, 1, "Ceviche extra"),
                                        new ItemPedidoRequest(3, 1, "Chicha morada")
                                ),
                                "uuid-prb106-mesa8-amp"
                        ))))
                .andExpect(status().isCreated());

        // La fila más reciente de BARRA_FRIA volvió a PENDIENTE y la nueva estación BEBIDAS también se encoló en PENDIENTE
        List<TrabajoImpresion> barraFria = trabajoImpresionRepository
                .findByPedidoIdAndTipoAndEstacionOrderByFechaCreacionDescIdDesc(pedidoId, "COMANDA", "BARRA_FRIA");
        assertEquals(2, barraFria.size());
        assertEquals("PENDIENTE", barraFria.get(0).getEstado());

        List<TrabajoImpresion> bebidas = trabajoImpresionRepository
                .findByPedidoIdAndTipoAndEstacionOrderByFechaCreacionDescIdDesc(pedidoId, "COMANDA", "BEBIDAS");
        assertEquals(1, bebidas.size());
        assertEquals("PENDIENTE", bebidas.get(0).getEstado());
    }
}
