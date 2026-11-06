package com.lacasablanca.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class HealthController {

    private static final String VERSION_SISTEMA = "0.2.0";

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping({"/api/v1/health", "/health"})
    public ResponseEntity<Map<String, Object>> healthCheck() {
        boolean dbOk = verificarBaseDeDatos();
        long uptimeSeg = ManagementFactory.getRuntimeMXBean().getUptime() / 1000L;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", dbOk ? "UP" : "DOWN");
        body.put("db", dbOk ? "UP" : "DOWN");
        body.put("version", VERSION_SISTEMA);
        body.put("uptime_seg", uptimeSeg);

        HttpStatus status = dbOk ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(body);
    }

    @GetMapping({"/api/v1/version", "/version"})
    public ResponseEntity<Map<String, Object>> versionInfo() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", VERSION_SISTEMA);
        body.put("servicio", "restaurante-backend-java");
        body.put("entorno", "produccion-lan");
        return ResponseEntity.ok(body);
    }

    private boolean verificarBaseDeDatos() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (SQLException ex) {
            return false;
        }
    }
}
