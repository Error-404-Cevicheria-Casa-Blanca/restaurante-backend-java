package com.lacasablanca.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lacasablanca.backend.dto.EstadoMesaRequest;
import com.lacasablanca.backend.dto.LoginRequest;
import com.lacasablanca.backend.model.EstadoMesa;
import com.lacasablanca.backend.model.Mesa;
import com.lacasablanca.backend.repository.MesaRepository;
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
import static org.junit.jupiter.api.Assertions.*;
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

    @Autowired
    private MesaRepository mesaRepository;

    private String tokenMozo;
    private String tokenCocinero;

    @BeforeEach
    void prepararDatosYTokens() throws Exception {
        // Aseguramos estados iniciales limpios y sin residuales de mozo ni fechas
        Mesa mesa1 = mesaRepository.findById(1).orElse(null);
        if (mesa1 != null) {
            mesa1.setEstado(EstadoMesa.LIBRE);
            mesa1.setMozo(null);
            mesa1.setFechaApertura(null);
            mesa1.setFechaCierre(null);
            mesaRepository.save(mesa1);
        }
        Mesa mesa2 = mesaRepository.findById(2).orElse(null);
        if (mesa2 != null) {
            mesa2.setEstado(EstadoMesa.LIBRE);
            mesa2.setMozo(null);
            mesa2.setFechaApertura(null);
            mesa2.setFechaCierre(null);
            mesaRepository.save(mesa2);
        }
        Mesa mesa3 = mesaRepository.findById(3).orElse(null);
        if (mesa3 != null) {
            mesa3.setEstado(EstadoMesa.OCUPADA);
            mesa3.setMozo(null);
            mesa3.setFechaApertura(null);
            mesa3.setFechaCierre(null);
            mesaRepository.save(mesa3);
        }

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
    @DisplayName("PE-10 E2 & PRB-40: PATCH /api/v1/mesas/1/estado cambia a ATENDIDA registrando mozo y fecha, luego a OCUPADA (200 OK)")
    void cambiarEstadoMesaExitoso() throws Exception {
        EstadoMesaRequest reqAtendida = new EstadoMesaRequest(EstadoMesa.ATENDIDA);
        mockMvc.perform(patch("/api/v1/mesas/1/estado")
                .header("Authorization", "Bearer " + tokenMozo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqAtendida)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numero").value(1))
                .andExpect(jsonPath("$.estado").value("ATENDIDA"));

        Mesa m = mesaRepository.findById(1).orElseThrow();
        assertNotNull(m.getMozo());
        assertEquals("mozo_01", m.getMozo().getUsername());
        assertNotNull(m.getFechaApertura());
        assertNull(m.getFechaCierre());

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
    @DisplayName("PRB-40 Transición Válida: ATENDIDA -> LIBRE registra fecha_cierre y retorna 200 OK")
    void transicionAtendidaALibreRetorna200() throws Exception {
        // Ponemos mesa 2 en ATENDIDA
        mockMvc.perform(patch("/api/v1/mesas/2/estado")
                .header("Authorization", "Bearer " + tokenMozo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new EstadoMesaRequest(EstadoMesa.ATENDIDA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATENDIDA"));

        // Liberamos la mesa a LIBRE
        mockMvc.perform(patch("/api/v1/mesas/2/estado")
                .header("Authorization", "Bearer " + tokenMozo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new EstadoMesaRequest(EstadoMesa.LIBRE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("LIBRE"));

        Mesa mLibre = mesaRepository.findById(2).orElseThrow();
        assertNotNull(mLibre.getFechaCierre());
    }

    @Test
    @DisplayName("PRB-40 Idempotencia 200: Mesa 1 LIBRE -> LIBRE es idempotente y retorna 200 OK")
    void mismoEstadoLibreRetorna200() throws Exception {
        EstadoMesaRequest req = new EstadoMesaRequest(EstadoMesa.LIBRE);
        mockMvc.perform(patch("/api/v1/mesas/1/estado")
                .header("Authorization", "Bearer " + tokenMozo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("LIBRE"));
    }

    @Test
    @DisplayName("PRB-40 Conflicto 409: Intentar abrir mesa ya OCUPADA retorna 409 Conflict")
    void abrirMesaYaOcupadaRetorna409() throws Exception {
        EstadoMesaRequest req = new EstadoMesaRequest(EstadoMesa.ATENDIDA);
        mockMvc.perform(patch("/api/v1/mesas/3/estado")
                .header("Authorization", "Bearer " + tokenMozo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("La mesa 3 no está disponible. Estado actual: OCUPADA"));
    }

    @Test
    @DisplayName("PRB-40 Conflicto 409: Transición inválida de LIBRE a CUENTA_SOLICITADA retorna 409 Conflict")
    void transicionInvalidaRetorna409() throws Exception {
        EstadoMesaRequest req = new EstadoMesaRequest(EstadoMesa.CUENTA_SOLICITADA);
        mockMvc.perform(patch("/api/v1/mesas/1/estado")
                .header("Authorization", "Bearer " + tokenMozo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    @DisplayName("PRB-40 Conflicto 409: Enviar mismo estado a mesa ya OCUPADA retorna 409 Conflict")
    void mismoEstadoRetorna409() throws Exception {
        EstadoMesaRequest req = new EstadoMesaRequest(EstadoMesa.OCUPADA);
        mockMvc.perform(patch("/api/v1/mesas/3/estado")
                .header("Authorization", "Bearer " + tokenMozo)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("La mesa 3 ya se encuentra en estado OCUPADA"));
    }

    @Test
    @DisplayName("PE-10 E3: Petición sin token JWT a /api/v1/mesas retorna 401 Unauthorized")
    void accesoSinTokenRechazado() throws Exception {
        mockMvc.perform(get("/api/v1/mesas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    @DisplayName("PE-10 Validacion 400: Estado inválido (ej. EN_ATENCION) retorna 400 Bad Request con {mensaje}")
    void cambiarEstadoInvalidoRetorna400() throws Exception {
        mockMvc.perform(patch("/api/v1/mesas/1/estado")
                .header("Authorization", "Bearer " + tokenMozo)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\": \"EN_ATENCION\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    @DisplayName("PE-10 Validacion 404: Mesa inexistente retorna 404 Not Found con {mensaje}")
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
