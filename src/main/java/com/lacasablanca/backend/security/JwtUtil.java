package com.lacasablanca.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms:43200000}")
    private long expirationMs;

    private final com.lacasablanca.backend.repository.TokenRevocadoRepository tokenRevocadoRepository;
    private final Set<String> tokensRevocados = ConcurrentHashMap.newKeySet();

    public JwtUtil(com.lacasablanca.backend.repository.TokenRevocadoRepository tokenRevocadoRepository) {
        this.tokenRevocadoRepository = tokenRevocadoRepository;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generarToken(Integer usuarioId, String username, String rol) {
        return generarTokenConExpiracion(usuarioId, username, rol, expirationMs);
    }

    public String generarTokenConExpiracion(Integer usuarioId, String username, String rol, long customExpirationMs) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + customExpirationMs);
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(usuarioId))
                .claim("nombre", username)
                .claim("rol", rol)
                .issuedAt(now)
                .expiration(exp)
                .signWith(getSigningKey())
                .compact();
    }

    public Claims extraerClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private String calcularHash(String token) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(token.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible", e);
        }
    }

    public void revocarToken(String token) {
        if (token != null && !token.isBlank()) {
            String hash = calcularHash(token);
            tokensRevocados.add(hash);
            try {
                if (tokenRevocadoRepository != null && !tokenRevocadoRepository.existsByTokenHash(hash)) {
                    tokenRevocadoRepository.save(new com.lacasablanca.backend.model.TokenRevocado(hash));
                }
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(JwtUtil.class)
                        .debug("Error persistiendo token revocado en BD: {}", e.getMessage());
            }
        }
    }

    public boolean esTokenRevocado(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String hash = calcularHash(token);
        if (tokensRevocados.contains(hash)) {
            return true;
        }
        try {
            if (tokenRevocadoRepository != null && tokenRevocadoRepository.existsByTokenHash(hash)) {
                tokensRevocados.add(hash);
                return true;
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(JwtUtil.class)
                    .debug("Error consultando token revocado en BD: {}", e.getMessage());
        }
        return false;
    }

    public void limpiarCacheParaPruebas() {
        tokensRevocados.clear();
    }
}
