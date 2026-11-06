package com.lacasablanca.backend.controller;

import com.lacasablanca.backend.dto.LoginRequest;
import com.lacasablanca.backend.dto.LoginResponse;
import com.lacasablanca.backend.dto.PinLoginRequest;
import com.lacasablanca.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/pin")
    public ResponseEntity<LoginResponse> loginPorPin(@Valid @RequestBody PinLoginRequest request) {
        return ResponseEntity.ok(authService.autenticarPorPin(request));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> perfilActual(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
                "usuario_id", authentication.getName(),
                "roles", authentication.getAuthorities()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.cerrarSesion(authHeader);
        return ResponseEntity.noContent().build();
    }
}
