package com.lacasablanca.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lacasablanca.backend.dto.EstadoMesaRequest;
import com.lacasablanca.backend.dto.LoginRequest;
import com.lacasablanca.backend.model.EstadoMesa;
import com.lacasablanca.backend.model.Mesa;
import com.lacasablanca.backend.repository.MesaRepository;
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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MesaConflicto409IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MesaRepository mesaRepository;

    private static final int MESA_PRUEBA_NUMERO = 6;
    private Integer mesaId;
    private String tokenMozo;

    @BeforeEach
    void prepararDatos() throws Exception {
        Mesa mesa = mesaRepository.findById(MESA_PRUEBA_NUMERO).orElse(null);
        if (mesa != null) {
            mesa.setEstado(EstadoMesa.LIBRE);
            mesa.setMozo(null);
            mesa.setFechaApertura(null);
            mesa.setFechaCierre(null);
            this.mesaId = mesaRepository.save(mesa).getId();
        } else {
            Mesa nueva = new Mesa(MESA_PRUEBA_NUMERO, 4, "Primer piso", EstadoMesa.LIBRE);
            this.mesaId = mesaRepository.save(nueva).getId();
        }

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
    void limpiarEstado() {
        if (mesaId != null) {
            Mesa mesa = mesaRepository.findById(mesaId).orElse(null);
            if (mesa != null) {
                mesa.setEstado(EstadoMesa.LIBRE);
                mesa.setMozo(null);
                mesa.setFechaApertura(null);
                mesa.setFechaCierre(null);
                mesaRepository.save(mesa);
            }
        }
    }

    @Test
    @DisplayName("PRB-44: Dos solicitudes PATCH simultáneas sobre la misma mesa libre: la primera responde 200 y la segunda 409 Conflict")
    void dosPatchSimultaneosMismaMesaGeneran409Determinista() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch salida = new CountDownLatch(1);

        String payload = objectMapper.writeValueAsString(new EstadoMesaRequest(EstadoMesa.ATENDIDA));
        String authHeader = "Bearer " + tokenMozo;

        Callable<MvcResult> tareaPatch = () -> {
            listos.countDown();
            boolean inicio = salida.await(5, TimeUnit.SECONDS);
            if (!inicio) {
                throw new IllegalStateException("Timeout esperando la señal de salida para la carrera concurrente");
            }
            return mockMvc.perform(patch("/api/v1/mesas/" + mesaId + "/estado")
                    .header("Authorization", authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload))
                    .andReturn();
        };

        Future<MvcResult> f1 = pool.submit(tareaPatch);
        Future<MvcResult> f2 = pool.submit(tareaPatch);

        boolean alineados = listos.await(5, TimeUnit.SECONDS);
        assertTrue(alineados, "Los dos hilos debieron estar alineados antes del disparo");
        salida.countDown();

        MvcResult r1 = f1.get(10, TimeUnit.SECONDS);
        MvcResult r2 = f2.get(10, TimeUnit.SECONDS);
        pool.shutdownNow();

        List<Integer> estadosHttp = List.of(
                r1.getResponse().getStatus(),
                r2.getResponse().getStatus()
        );

        // Criterio de aceptación Jira PRB-44: exactamente una solicitud gana (200) y la otra recibe 409
        assertEquals(1, Collections.frequency(estadosHttp, 200),
                "Exactamente una solicitud PATCH debió responder 200 OK");
        assertEquals(1, Collections.frequency(estadosHttp, 409),
                "Exactamente una solicitud PATCH debió responder 409 Conflict");

        // Validar cuerpo de la respuesta con conflicto 409
        MvcResult res409 = (r1.getResponse().getStatus() == 409) ? r1 : r2;
        String cuerpoError = res409.getResponse().getContentAsString();
        JsonNode jsonError = objectMapper.readTree(cuerpoError);
        assertNotNull(jsonError.get("mensaje"), "La respuesta 409 debe incluir campo mensaje");
        assertTrue(jsonError.get("mensaje").asText().contains("mesa"), "El mensaje de 409 debe referenciar a la mesa");

        // Validar estado consistente en la base de datos de prueba
        Mesa mesaFinal = mesaRepository.findById(mesaId).orElseThrow();
        assertEquals(EstadoMesa.ATENDIDA, mesaFinal.getEstado());
        assertNotNull(mesaFinal.getMozo(), "La mesa debe tener asignado el mozo ganador");
        assertEquals("mozo_01", mesaFinal.getMozo().getUsername());
        assertNotNull(mesaFinal.getFechaApertura(), "La fecha de apertura debe quedar registrada");
    }

    @Test
    @DisplayName("PRB-44 E2: PATCH repetido a ATENDIDA sobre mesa ya ATENDIDA retorna 409 Conflict")
    void patchMismoEstadoAtendidaRetorna409() throws Exception {
        String authHeader = "Bearer " + tokenMozo;
        EstadoMesaRequest req = new EstadoMesaRequest(EstadoMesa.ATENDIDA);

        // Primer PATCH -> 200 OK
        mockMvc.perform(patch("/api/v1/mesas/" + mesaId + "/estado")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATENDIDA"));

        // Segundo PATCH con el mismo estado -> 409 Conflict
        mockMvc.perform(patch("/api/v1/mesas/" + mesaId + "/estado")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("La mesa " + MESA_PRUEBA_NUMERO + " ya se encuentra en estado ATENDIDA"));
    }

    @Test
    @DisplayName("PRB-44 E3: PATCH con transición inválida de LIBRE a CUENTA_SOLICITADA retorna 409 Conflict")
    void patchTransicionInvalidaDesdeLibreRetorna409() throws Exception {
        String authHeader = "Bearer " + tokenMozo;
        EstadoMesaRequest reqInvalido = new EstadoMesaRequest(EstadoMesa.CUENTA_SOLICITADA);

        mockMvc.perform(patch("/api/v1/mesas/" + mesaId + "/estado")
                .header("Authorization", authHeader)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reqInvalido)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Transición de estado no permitida: de LIBRE a CUENTA_SOLICITADA para la mesa " + MESA_PRUEBA_NUMERO));
    }
}
