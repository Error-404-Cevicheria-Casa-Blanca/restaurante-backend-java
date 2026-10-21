package com.lacasablanca.backend.controller;

import com.lacasablanca.backend.dto.LoginRequest;
import com.lacasablanca.backend.dto.LoginResponse;
import com.lacasablanca.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.autenticar(request));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> perfilActual(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
                "usuario_id", authentication.getName(),
                "roles", authentication.getAuthorities()
        ));
    }
}
