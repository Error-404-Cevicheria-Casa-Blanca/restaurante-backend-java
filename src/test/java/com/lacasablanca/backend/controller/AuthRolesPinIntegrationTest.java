package com.lacasablanca.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lacasablanca.backend.dto.CobrarRequest;
import com.lacasablanca.backend.dto.CrearPedidoRequest;
import com.lacasablanca.backend.dto.EstadoMesaRequest;
import com.lacasablanca.backend.dto.ItemPedidoRequest;
import com.lacasablanca.backend.dto.LoginRequest;
import com.lacasablanca.backend.dto.PagoRequestDto;
import com.lacasablanca.backend.dto.PinLoginRequest;
import com.lacasablanca.backend.model.EstadoMesa;
import com.lacasablanca.backend.model.Mesa;
import com.lacasablanca.backend.repository.MesaRepository;
import com.lacasablanca.backend.repository.PedidoRepository;
import com.lacasablanca.backend.service.AuthService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthRolesPinIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthService authService;

    @Autowired
    private MesaRepository mesaRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private com.lacasablanca.backend.repository.UsuarioRepository usuarioRepository;

    @Autowired
    private com.lacasablanca.backend.repository.RolRepository rolRepository;

    @BeforeEach
    void setUp() {
        authService.limpiarBloqueos();
        pedidoRepository.deleteAll();
        for (Mesa m : mesaRepository.findAll()) {
            m.setEstado(EstadoMesa.LIBRE);
            m.setMozo(null);
            m.setFechaApertura(null);
            m.setFechaCierre(null);
            mesaRepository.save(m);
        }
    }

    private String loginPorPin(String usuario, String pin) throws Exception {
        PinLoginRequest req = new PinLoginRequest(usuario, pin);
        MvcResult res = mockMvc.perform(post("/api/v1/auth/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(res.getResponse().getContentAsString());
        return node.get("token").asText();
    }

    @Test
    @DisplayName("PRB-54: Login por PIN de 4 dígitos con BCrypt autentica correctamente a MOZO, COCINERO, CAJERO y ADMIN")
    void loginPorPinExitosoParaTodosLosRoles() throws Exception {
        mockMvc.perform(post("/api/v1/auth/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PinLoginRequest("mozo_01", "1234"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.usuario.nombre").value("mozo_01"))
                .andExpect(jsonPath("$.usuario.rol").value("MOZO"));

        mockMvc.perform(post("/api/v1/auth/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PinLoginRequest("cocinero_01", "3456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.rol").value("COCINERO"));

        mockMvc.perform(post("/api/v1/auth/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PinLoginRequest("cajero_01", "4567"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.rol").value("CAJERO"));

        mockMvc.perform(post("/api/v1/auth/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PinLoginRequest("admin_test", "9999"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.rol").value("ADMIN"));
    }

    @Test
    @DisplayName("PRB-54: PIN con formato distinto a 4 dígitos numéricos retorna 400 Bad Request")
    void loginPorPinFormatoInvalidoRetorna400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PinLoginRequest("mozo_01", "12a4"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El PIN debe contener exactamente 4 dígitos numéricos"));

        mockMvc.perform(post("/api/v1/auth/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PinLoginRequest("mozo_01", "123"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PRB-54: Bloqueo temporal tras 5 intentos fallidos retorna 423 Locked")
    void bloqueoTemporalTras5IntentosFallidosRetorna423() throws Exception {
        PinLoginRequest reqFallido = new PinLoginRequest("mozo_02", "0000");

        for (int i = 1; i <= 5; i++) {
            mockMvc.perform(post("/api/v1/auth/pin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(reqFallido)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.mensaje").value("Usuario o PIN incorrectos"));
        }

        assertEquals(5, authService.obtenerIntentosFallidos("mozo_02"));
        assertTrue(authService.estaBloqueado("mozo_02"));

        // El 6to intento (incluso con el PIN correcto o contraseña correcta) responde 423 Locked
        mockMvc.perform(post("/api/v1/auth/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PinLoginRequest("mozo_02", "2345"))))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.codigo").value("CUENTA_BLOQUEADA"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("mozo_02", "password123"))))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.codigo").value("CUENTA_BLOQUEADA"));
    }

    @Test
    @DisplayName("PRB-54: POST /api/v1/auth/logout revoca el JWT e impide su uso posterior (401)")
    void logoutRevocaTokenCorrectamente() throws Exception {
        String token = loginPorPin("mozo_01", "1234");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario_id").exists());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PRB-54: Matriz de permisos por rol (MOZO, COCINERO, CAJERO, ADMIN)")
    void matrizDePermisosPorRol() throws Exception {
        String tokenMozo = loginPorPin("mozo_01", "1234");
        String tokenCocinero = loginPorPin("cocinero_01", "3456");
        String tokenCajero = loginPorPin("cajero_01", "4567");
        String tokenAdmin = loginPorPin("admin_test", "9999");

        CrearPedidoRequest pedidoReq = new CrearPedidoRequest(
                1,
                List.of(new ItemPedidoRequest(1, 1, "sin picante")),
                "uuid-matriz-roles-001"
        );

        // 1. COCINERO y CAJERO NO pueden crear pedidos (403 Forbidden)
        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenCocinero)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoReq)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenCajero)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoReq)))
                .andExpect(status().isForbidden());

        // 2. CAJERO tampoco puede cambiar estado de mesa manualmente (403 Forbidden)
        mockMvc.perform(patch("/api/v1/mesas/1/estado")
                        .header("Authorization", "Bearer " + tokenCajero)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EstadoMesaRequest(EstadoMesa.ATENDIDA))))
                .andExpect(status().isForbidden());

        // 3. MOZO SÍ puede crear el pedido (201 Created)
        mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pedidoReq)))
                .andExpect(status().isCreated());

        // 4. COCINERO NO puede consultar cuenta ni cobrar mesa (403 Forbidden)
        mockMvc.perform(get("/api/v1/mesas/1/cuenta")
                        .header("Authorization", "Bearer " + tokenCocinero))
                .andExpect(status().isForbidden());

        CobrarRequest cobrarReq = new CobrarRequest(
                List.of(new PagoRequestDto("EFECTIVO", new BigDecimal("48.00"), null)),
                BigDecimal.ZERO,
                "Cobro por cajero",
                "cobro-cajero-001"
        );

        mockMvc.perform(post("/api/v1/mesas/1/cobrar")
                        .header("Authorization", "Bearer " + tokenCocinero)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cobrarReq)))
                .andExpect(status().isForbidden());

        // 5. CAJERO SÍ puede consultar cuenta (200 OK) y cobrar la mesa (201 Created)
        mockMvc.perform(get("/api/v1/mesas/1/cuenta")
                        .header("Authorization", "Bearer " + tokenCajero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(48.00));

        mockMvc.perform(post("/api/v1/mesas/1/cobrar")
                        .header("Authorization", "Bearer " + tokenCajero)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cobrarReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pagado").value(48.00));

        // 6. ADMIN tiene acceso completo a mesas y pedidos
        mockMvc.perform(patch("/api/v1/mesas/2/estado")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EstadoMesaRequest(EstadoMesa.ATENDIDA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATENDIDA"));
    }

    @Test
    @DisplayName("Usuario sin PIN configurado no puede autenticar por PIN ni cae a clave BCrypt")
    void loginPinUsuarioSinPinConfiguradoFalla() throws Exception {
        com.lacasablanca.backend.model.Usuario nuevo = new com.lacasablanca.backend.model.Usuario();
        nuevo.setUsername("mozo_sin_pin_test");
        nuevo.setClaveHash("$2a$10$dummyHashDePassword12345678901234567890123456789012");
        nuevo.setRol(rolRepository.findByNombre("MOZO").orElseThrow());
        usuarioRepository.save(nuevo);

        PinLoginRequest req = new PinLoginRequest("mozo_sin_pin_test", "1234");
        mockMvc.perform(post("/api/v1/auth/login-pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }
}
