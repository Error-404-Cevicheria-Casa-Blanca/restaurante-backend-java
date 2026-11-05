package com.lacasablanca.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lacasablanca.backend.dto.CobrarRequest;
import com.lacasablanca.backend.dto.CrearPedidoRequest;
import com.lacasablanca.backend.dto.EstadoMesaRequest;
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

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MesaCobroIntegrationTest {

    private static final int MESA_ID = 3;

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
        // Autenticar mozo para obtener JWT
        LoginRequest loginRequest = new LoginRequest("mozo_01", "password123");
        MvcResult resLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(resLogin.getResponse().getContentAsString());
        tokenMozo = json.get("token").asText();

        // Limpieza de estado de la mesa de prueba
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

    private void crearPedidoPruebaMesa(int mesaId) throws Exception {
        // 1. Abrir mesa a ATENDIDA
        EstadoMesaRequest reqApertura = new EstadoMesaRequest(EstadoMesa.ATENDIDA);
        mockMvc.perform(patch("/api/v1/mesas/" + mesaId + "/estado")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqApertura)))
                .andExpect(status().isOk());

        // 2. Crear pedido con 2 Chicharrones (id 2, S/ 35.00 c/u) y 1 Chicha (id 3, S/ 15.00) = S/ 85.00
        List<ItemPedidoRequest> items = List.of(
                new ItemPedidoRequest(2, 2, "Bien crocante"),
                new ItemPedidoRequest(3, 1, "Helada")
        );
        CrearPedidoRequest reqPedido = new CrearPedidoRequest(mesaId, items);

        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqPedido)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("GET /api/v1/mesas/{id}/cuenta retorna desglose tributario e items consolidados")
    void obtenerCuentaRetornaDesgloseCorrecto() throws Exception {
        crearPedidoPruebaMesa(MESA_ID);

        mockMvc.perform(get("/api/v1/mesas/" + MESA_ID + "/cuenta")
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesa_id").value(MESA_ID))
                .andExpect(jsonPath("$.total").value(85.00))
                .andExpect(jsonPath("$.subtotal").value(72.03))
                .andExpect(jsonPath("$.igv").value(12.97))
                .andExpect(jsonPath("$.propina_sugerida").value(8.50))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.pedidos", hasSize(1)));
    }

    @Test
    @DisplayName("GET /api/v1/mesas/{id}/cuenta en mesa LIBRE retorna 409 Conflict")
    void obtenerCuentaMesaLibreRetorna409() throws Exception {
        mockMvc.perform(get("/api/v1/mesas/" + MESA_ID + "/cuenta")
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("La mesa 3 está LIBRE y no tiene cuenta activa"));
    }

    @Test
    @DisplayName("POST /api/v1/mesas/{id}/precuenta transiciona mesa OCUPADA a CUENTA_SOLICITADA")
    void solicitarPrecuentaTransicionaEstadoMesa() throws Exception {
        crearPedidoPruebaMesa(MESA_ID);

        mockMvc.perform(post("/api/v1/mesas/" + MESA_ID + "/precuenta")
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mesa_id").value(MESA_ID))
                .andExpect(jsonPath("$.total").value(85.00));

        Mesa mesa = mesaRepository.findById(MESA_ID).orElseThrow();
        assertEquals(EstadoMesa.CUENTA_SOLICITADA, mesa.getEstado());
    }

    @Test
    @DisplayName("POST /api/v1/mesas/{id}/cobrar con EFECTIVO liquida pedidos, calcula vuelto y libera mesa")
    void cobrarConEfectivoExitoso() throws Exception {
        crearPedidoPruebaMesa(MESA_ID);

        // Cliente paga con billete de S/ 100.00 para una cuenta de S/ 85.00 -> vuelto S/ 15.00
        List<PagoRequestDto> pagos = List.of(
                new PagoRequestDto("EFECTIVO", BigDecimal.valueOf(100.00), null)
        );
        CobrarRequest reqCobro = new CobrarRequest(
                pagos,
                BigDecimal.ZERO,
                "Cobro en efectivo mesa 3",
                UUID.randomUUID().toString()
        );

        mockMvc.perform(post("/api/v1/mesas/" + MESA_ID + "/cobrar")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqCobro)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(85.00))
                .andExpect(jsonPath("$.pagado").value(100.00))
                .andExpect(jsonPath("$.vuelto").value(15.00))
                .andExpect(jsonPath("$.pedidos[0].estado").value("COBRADO"));

        // Validar persistencia en base de datos H2
        Mesa mesa = mesaRepository.findById(MESA_ID).orElseThrow();
        assertEquals(EstadoMesa.LIBRE, mesa.getEstado());
        assertNull(mesa.getMozo());
        assertNotNull(mesa.getFechaCierre());

        List<Pedido> pedidos = pedidoRepository.findByMesaIdOrderByFechaCreacionDesc(MESA_ID);
        assertEquals(EstadoPedido.COBRADO, pedidos.get(0).getEstado());
    }

    @Test
    @DisplayName("POST /api/v1/mesas/{id}/cobrar con YAPE y referencia liquida exactamente la cuenta")
    void cobrarConYapeExitoso() throws Exception {
        crearPedidoPruebaMesa(MESA_ID);

        List<PagoRequestDto> pagos = List.of(
                new PagoRequestDto("YAPE", BigDecimal.valueOf(85.00), "OPERACION-987654")
        );
        CobrarRequest reqCobro = new CobrarRequest(
                pagos,
                BigDecimal.ZERO,
                "Pago vía QR Yape",
                UUID.randomUUID().toString()
        );

        mockMvc.perform(post("/api/v1/mesas/" + MESA_ID + "/cobrar")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqCobro)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(85.00))
                .andExpect(jsonPath("$.pagado").value(85.00))
                .andExpect(jsonPath("$.vuelto").value(0.00));

        Mesa mesa = mesaRepository.findById(MESA_ID).orElseThrow();
        assertEquals(EstadoMesa.LIBRE, mesa.getEstado());
    }

    @Test
    @DisplayName("POST /api/v1/mesas/{id}/cobrar con YAPE sin referencia retorna 400 Bad Request")
    void cobrarConYapeSinReferenciaRetorna400() throws Exception {
        crearPedidoPruebaMesa(MESA_ID);

        List<PagoRequestDto> pagos = List.of(
                new PagoRequestDto("YAPE", BigDecimal.valueOf(85.00), null)
        );
        CobrarRequest reqCobro = new CobrarRequest(
                pagos,
                BigDecimal.ZERO,
                "Yape sin referencia",
                UUID.randomUUID().toString()
        );

        mockMvc.perform(post("/api/v1/mesas/" + MESA_ID + "/cobrar")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqCobro)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/mesas/{id}/cobrar con monto insuficiente retorna 400 Bad Request")
    void cobrarConMontoInsuficienteRetorna400() throws Exception {
        crearPedidoPruebaMesa(MESA_ID);

        // Cuenta es S/ 82.00, pero envía solo S/ 50.00
        List<PagoRequestDto> pagos = List.of(
                new PagoRequestDto("EFECTIVO", BigDecimal.valueOf(50.00), null)
        );
        CobrarRequest reqCobro = new CobrarRequest(
                pagos,
                BigDecimal.ZERO,
                "Pago incompleto",
                UUID.randomUUID().toString()
        );

        mockMvc.perform(post("/api/v1/mesas/" + MESA_ID + "/cobrar")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqCobro)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/mesas/{id}/cobrar es idempotente ante reintentos con el mismo client_request_id")
    void cobrarConMismoClientRequestIdEsIdempotente() throws Exception {
        crearPedidoPruebaMesa(MESA_ID);

        String requestId = UUID.randomUUID().toString();
        List<PagoRequestDto> pagos = List.of(
                new PagoRequestDto("PLIN", BigDecimal.valueOf(85.00), "PLIN-555111")
        );
        CobrarRequest reqCobro = new CobrarRequest(pagos, BigDecimal.ZERO, "Pago Plin", requestId);

        // Primera llamada: liquida la cuenta y libera la mesa
        mockMvc.perform(post("/api/v1/mesas/" + MESA_ID + "/cobrar")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqCobro)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(85.00))
                .andExpect(jsonPath("$.pagado").value(85.00));

        // Segunda llamada con el mismo client_request_id: replay idempotente sin arrojar 409
        mockMvc.perform(post("/api/v1/mesas/" + MESA_ID + "/cobrar")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqCobro)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(85.00))
                .andExpect(jsonPath("$.pagado").value(85.00))
                .andExpect(jsonPath("$.pedidos[0].estado").value("COBRADO"));
    }

    @Test
    @DisplayName("POST /api/v1/mesas/{id}/cobrar soporta pago mixto (EFECTIVO + TARJETA) con propina")
    void cobrarConPagoMixtoYPropinaExitoso() throws Exception {
        crearPedidoPruebaMesa(MESA_ID);

        // Consumo S/ 85.00 + Propina S/ 5.00 = S/ 90.00 -> Tarjeta S/ 50.00 + Efectivo S/ 50.00 -> Vuelto S/ 10.00
        List<PagoRequestDto> pagos = List.of(
                new PagoRequestDto("TARJETA", BigDecimal.valueOf(50.00), "VOUCHER-7788"),
                new PagoRequestDto("EFECTIVO", BigDecimal.valueOf(50.00), null)
        );
        CobrarRequest reqCobro = new CobrarRequest(
                pagos,
                BigDecimal.valueOf(5.00),
                "Pago mixto con propina",
                UUID.randomUUID().toString()
        );

        mockMvc.perform(post("/api/v1/mesas/" + MESA_ID + "/cobrar")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqCobro)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(85.00))
                .andExpect(jsonPath("$.pagado").value(100.00))
                .andExpect(jsonPath("$.vuelto").value(10.00));
    }

    @Test
    @DisplayName("POST /api/v1/mesas/{id}/cobrar con descuadre de exceso en pago digital retorna 400 Bad Request")
    void cobrarConExcesoEnPagoDigitalRetorna400() throws Exception {
        crearPedidoPruebaMesa(MESA_ID);

        List<PagoRequestDto> pagos = List.of(
                new PagoRequestDto("YAPE", BigDecimal.valueOf(100.00), "OP-EXCESO-01")
        );
        CobrarRequest reqCobro = new CobrarRequest(
                pagos,
                BigDecimal.ZERO,
                "Exceso sin efectivo",
                UUID.randomUUID().toString()
        );

        mockMvc.perform(post("/api/v1/mesas/" + MESA_ID + "/cobrar")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqCobro)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/v1/pedidos/{id}/pagos liquida un pedido individual y libera la mesa al completarse")
    void cobrarPedidoIndividualExitoso() throws Exception {
        crearPedidoPruebaMesa(MESA_ID);

        Pedido pedido = pedidoRepository.findByMesaIdOrderByFechaCreacionDesc(MESA_ID).get(0);

        List<PagoRequestDto> pagos = List.of(
                new PagoRequestDto("EFECTIVO", BigDecimal.valueOf(85.00), null)
        );
        CobrarRequest reqCobro = new CobrarRequest(
                pagos,
                BigDecimal.ZERO,
                "Pago individual por pedido",
                UUID.randomUUID().toString()
        );

        mockMvc.perform(post("/api/v1/pedidos/" + pedido.getId() + "/pagos")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqCobro)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(85.00))
                .andExpect(jsonPath("$.pagado").value(85.00))
                .andExpect(jsonPath("$.vuelto").value(0.00))
                .andExpect(jsonPath("$.pedidos[0].estado").value("COBRADO"));

        Mesa mesa = mesaRepository.findById(MESA_ID).orElseThrow();
        assertEquals(EstadoMesa.LIBRE, mesa.getEstado());
    }

    @Test
    @DisplayName("Endpoints de cobro sin token JWT retornan 401 Unauthorized")
    void endpointsCobroSinTokenRetornan401() throws Exception {
        mockMvc.perform(get("/api/v1/mesas/" + MESA_ID + "/cuenta"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/mesas/" + MESA_ID + "/cobrar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/mesas/" + MESA_ID + "/precuenta"))
                .andExpect(status().isUnauthorized());
    }
}
