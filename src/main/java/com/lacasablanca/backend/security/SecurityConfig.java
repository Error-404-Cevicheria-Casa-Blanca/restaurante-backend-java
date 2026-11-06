package com.lacasablanca.backend.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
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
