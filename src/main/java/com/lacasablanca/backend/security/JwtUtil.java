package com.lacasablanca.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

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
}
