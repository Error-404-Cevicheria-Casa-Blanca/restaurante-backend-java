package com.lacasablanca.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lacasablanca.backend.dto.LoginRequest;
import com.lacasablanca.backend.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    @DisplayName("PE-9 E1: Login exitoso con {usuario, clave} retorna 200 y {token, tipo, usuario:{id, nombre, rol}}")
    void loginExitoso() throws Exception {
        LoginRequest req = new LoginRequest("mozo_01", "password123");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.usuario.id").isNumber())
                .andExpect(jsonPath("$.usuario.nombre").value("mozo_01"))
                .andExpect(jsonPath("$.usuario.rol").value("MOZO"))
                .andExpect(jsonPath("$.usuario.nombre_visible").value("Mozo Salón (Piso 1)"))
                .andExpect(jsonPath("$.usuario.turno").exists())
                .andExpect(jsonPath("$.fecha_hora_servidor").isNotEmpty());
    }

    @Test
    @DisplayName("PRB-85: Login exitoso con terminal_codigo vincula terminal y registra sesión")
    void loginExitosoConTerminal() throws Exception {
        LoginRequest req = new LoginRequest("mozo_01", "password123", "TERM-01");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.terminal").exists())
                .andExpect(jsonPath("$.usuario.terminal.codigo").value("TERM-01"))
                .andExpect(jsonPath("$.usuario.terminal.nombre").value("Tablet Salón Principal"))
                .andExpect(jsonPath("$.usuario.turno").exists())
                .andExpect(jsonPath("$.fecha_hora_servidor").isNotEmpty());
    }

    @Test
    @DisplayName("PRB-85: Login con terminal_codigo inexistente retorna 400 Bad Request")
    void loginTerminalInvalida() throws Exception {
        LoginRequest req = new LoginRequest("mozo_01", "password123", "TERM-INEXISTENTE");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Terminal inválida o no registrada: TERM-INEXISTENTE"));
    }

    @Test
    @DisplayName("PRB-85: GET /api/v1/auth/me autenticado retorna perfil completo (usuario, turno, terminal)")
    void authMeAutenticado() throws Exception {
        LoginRequest req = new LoginRequest("mozo_01", "password123", "TERM-01");
        String jsonRes = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(jsonRes).get("token").asText();

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario_id").exists())
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.usuario.nombre").value("mozo_01"))
                .andExpect(jsonPath("$.usuario.nombre_visible").value("Mozo Salón (Piso 1)"))
                .andExpect(jsonPath("$.usuario.rol").value("MOZO"))
                .andExpect(jsonPath("$.turno").exists())
                .andExpect(jsonPath("$.terminal.codigo").value("TERM-01"))
                .andExpect(jsonPath("$.fecha_hora_servidor").isNotEmpty());
    }

    @Test
    @DisplayName("PRB-85: GET /api/v1/auth/me sin token retorna 401 Unauthorized")
    void authMeSinToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("No autorizado: Token JWT requerido o inválido"));
    }

    @Test
    @DisplayName("PE-9 E2: Credenciales inválidas retorna 401 con cuerpo {mensaje: 'Usuario o contraseña incorrectos'}")
    void loginCredencialesInvalidas() throws Exception {
        LoginRequest req = new LoginRequest("mozo_01", "clave_incorrecta");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("Usuario o contraseña incorrectos"));
    }

    @Test
    @DisplayName("PE-9 E3 (D9): Token JWT expirado al consumir endpoint protegido retorna 403 Forbidden")
    void tokenExpiradoRetorna403() throws Exception {
        String tokenExpirado = jwtUtil.generarTokenConExpiracion(1, "mozo_01", "MOZO", -10000L);

        mockMvc.perform(get("/api/v1/auth/me")
                .header("Authorization", "Bearer " + tokenExpirado))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Token JWT expirado"));
    }

    @Test
    @DisplayName("PE-9 Validación: Campos vacíos retorna 400 Bad Request con {mensaje}")
    void loginCamposVacios() throws Exception {
        LoginRequest req = new LoginRequest("", "");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
    }
}
