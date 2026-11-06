package com.lacasablanca.backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHardeningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("PRB-67: CORS permite solicitudes preflight desde el origen autorizado del panel Django")
    void corsPermiteOrigenAutorizadoDelPanel() throws Exception {
        mockMvc.perform(options("/api/v1/mesas")
                        .header("Origin", "http://localhost:8000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8000"));
    }

    @Test
    @DisplayName("PRB-67: CORS rechaza solicitudes preflight desde orígenes externos no autorizados (403 Forbidden)")
    void corsRechazaOrigenExternoNoAutorizado() throws Exception {
        mockMvc.perform(options("/api/v1/mesas")
                        .header("Origin", "https://attacker.evil.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PRB-67: Respuestas incluyen cabeceras de endurecimiento HTTP (X-Content-Type-Options, X-Frame-Options, Referrer-Policy)")
    void respuestasIncluyenCabecerasSeguridadHttp() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "same-origin"));
    }

    @Test
    @DisplayName("PRB-67: Configuración de producción application.yml exige variables de entorno sin secretos por defecto")
    void applicationYmlSinSecretosHardcodeados() throws Exception {
        ClassPathResource resource = new ClassPathResource("application.yml");
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(content.contains("${DB_URL}"));
        assertTrue(content.contains("${DB_USER}"));
        assertTrue(content.contains("${DB_PASSWORD}"));
        assertTrue(content.contains("${JWT_SECRET}"));
        assertFalse(content.contains("password123"));
    }
}
