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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthCheckIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("PRB-64: GET /api/v1/health y /health son públicos en LAN y reportan estado de BD y uptime")
    void healthCheckPublicoReportaEstadoBaseDeDatos() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.db").value("UP"))
                .andExpect(jsonPath("$.version").value("0.2.0"))
                .andExpect(jsonPath("$.uptime_seg").isNumber());

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.db").value("UP"));
    }

    @Test
    @DisplayName("PRB-64: GET /api/v1/version y /version retornan metadatos de despliegue")
    void versionPublicoRetornaMetadatos() throws Exception {
        mockMvc.perform(get("/api/v1/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("0.2.0"))
                .andExpect(jsonPath("$.servicio").value("restaurante-backend-java"));

        mockMvc.perform(get("/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("0.2.0"));
    }

    @Test
    @DisplayName("PRB-64: Configuración de logs rotativos logback-spring.xml define retención y tamaño máximo")
    void logbackSpringXmlDefineRotacionYRetencion() throws Exception {
        ClassPathResource resource = new ClassPathResource("logback-spring.xml");
        assertTrue(resource.exists(), "Debe existir logback-spring.xml en resources");
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(content.contains("RollingFileAppender"));
        assertTrue(content.contains("SizeAndTimeBasedRollingPolicy"));
        assertTrue(content.contains("<maxFileSize>10MB</maxFileSize>"));
        assertTrue(content.contains("<maxHistory>30</maxHistory>"));
    }
}
