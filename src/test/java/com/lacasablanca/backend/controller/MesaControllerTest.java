package com.lacasablanca.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lacasablanca.backend.dto.EstadoMesaRequest;
import com.lacasablanca.backend.dto.LoginRequest;
import com.lacasablanca.backend.model.EstadoMesa;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MesaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenMozo;
    private String tokenCocinero;

    @BeforeEach
    void obtenerTokens() throws Exception {
        LoginRequest reqMozo = new LoginRequest("mozo_01", "password123");
        MvcResult resMozo = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqMozo)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode jsonMozo = objectMapper.readTree(resMozo.getResponse().getContentAsString());
        this.tokenMozo = jsonMozo.get("token").asText();

        LoginRequest reqCocinero = new LoginRequest("cocinero_01", "password123");
        MvcResult resCoc = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqCocinero)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode jsonCoc = objectMapper.readTree(resCoc.getResponse().getContentAsString());
        this.tokenCocinero = jsonCoc.get("token").asText();
    }

    @Test
    @DisplayName("PE-10 E1: GET /api/v1/mesas con token retorna 200 y las 13 mesas ordenadas")
    void listar13MesasExitoso() throws Exception {
        mockMvc.perform(get("/api/v1/mesas")
                .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(13)))
                .andExpect(jsonPath("$[0].numero").value(1))
                .andExpect(jsonPath("$[0].ubicacion").value("Primer piso"))
                .andExpect(jsonPath("$[12].numero").value(13))
                .andExpect(jsonPath("$[12].ubicacion").value("Segundo piso"));
    }

    @Test
    @DisplayName("PE-10 E2: PATCH /api/v1/mesas/1/estado cambia a ATENDIDA y luego a OCUPADA (200 OK)")
    void cambiarEstadoMesaExitoso() throws Exception {
        EstadoMesaRequest reqAtendida = new EstadoMesaRequest(EstadoMesa.ATENDIDA);
        mockMvc.perform(patch("/api/v1/mesas/1/estado")
                .header("Authorization", "Bearer " + tokenMozo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqAtendida)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.estado").value("ATENDIDA"));

        EstadoMesaRequest reqOcupada = new EstadoMesaRequest(EstadoMesa.OCUPADA);
        mockMvc.perform(patch("/api/v1/mesas/1/estado")
                .header("Authorization", "Bearer " + tokenMozo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqOcupada)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.estado").value("OCUPADA"));
    }

    @Test
    @DisplayName("PE-10 E3: Petición sin token JWT a /api/v1/mesas retorna 401 Unauthorized")
    void accesoSinTokenRechazado() throws Exception {
        mockMvc.perform(get("/api/v1/mesas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    @DisplayName("PE-10 Validación 400: Estado inválido (ej. EN_ATENCION) retorna 400 Bad Request con {mensaje}")
    void cambiarEstadoInvalidoRetorna400() throws Exception {
        mockMvc.perform(patch("/api/v1/mesas/1/estado")
                .header("Authorization", "Bearer " + tokenMozo)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\": \"EN_ATENCION\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    @DisplayName("PE-10 Validación 404: Mesa inexistente retorna 404 Not Found con {mensaje}")
    void cambiarEstadoMesaInexistenteRetorna404() throws Exception {
        EstadoMesaRequest req = new EstadoMesaRequest(EstadoMesa.OCUPADA);
        mockMvc.perform(patch("/api/v1/mesas/999/estado")
                .header("Authorization", "Bearer " + tokenMozo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    @DisplayName("PE-10 Seguridad de Roles: COCINERO no puede cambiar estado de mesa (403 Forbidden)")
    void cocineroNoPuedeCambiarEstadoMesa() throws Exception {
        EstadoMesaRequest req = new EstadoMesaRequest(EstadoMesa.OCUPADA);
        mockMvc.perform(patch("/api/v1/mesas/1/estado")
                .header("Authorization", "Bearer " + tokenCocinero)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").exists());
    }
}
