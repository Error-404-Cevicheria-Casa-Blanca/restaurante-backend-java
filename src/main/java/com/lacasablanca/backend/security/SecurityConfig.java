package com.lacasablanca.backend.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.cors.allowed-origins:http://localhost:8000,http://127.0.0.1:8000}")
    private String corsAllowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "Accept"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(Customizer.withDefaults())
                .referrerPolicy(ref -> ref.policy(
                        ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN
                ))
            )
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, authEx) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"mensaje\": \"No autorizado: Token JWT requerido o inválido\"}");
                })
                .accessDeniedHandler((req, res, accessEx) -> {
                    res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"mensaje\": \"Acceso denegado: permisos insuficientes\"}");
                })
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/auth/pin",
                    "/api/v1/health",
                    "/health",
                    "/api/v1/version",
                    "/version",
                    "/error"
                ).permitAll()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/mesas/*/estado").hasAnyRole("MOZO", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/mesas/*/cuenta").hasAnyRole("MOZO", "CAJERO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/mesas/*/precuenta").hasAnyRole("MOZO", "CAJERO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/mesas/*/cobrar").hasAnyRole("MOZO", "CAJERO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pedidos").hasAnyRole("MOZO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pedidos/*/anular").hasAnyRole("MOZO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pedidos/*/items/*/anular").hasAnyRole("MOZO", "ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/pedidos/*/pagos").hasAnyRole("MOZO", "CAJERO", "ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/v1/pedidos/*/estado")
                    .hasAnyRole("COCINERO", "MOZO", "CAJERO", "ADMIN")
                .requestMatchers("/api/v1/cocina/**").hasAnyRole("COCINERO", "ADMIN")
                .requestMatchers("/api/v1/caja/**").hasAnyRole("CAJERO", "ADMIN")
                .requestMatchers("/api/v1/inventario/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/reportes/**").hasAnyRole("CAJERO", "ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
