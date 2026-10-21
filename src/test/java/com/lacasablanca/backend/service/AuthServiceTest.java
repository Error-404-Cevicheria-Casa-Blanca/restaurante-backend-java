package com.lacasablanca.backend.service;

import com.lacasablanca.backend.dto.LoginRequest;
import com.lacasablanca.backend.dto.LoginResponse;
import com.lacasablanca.backend.model.Rol;
import com.lacasablanca.backend.model.Usuario;
import com.lacasablanca.backend.repository.UsuarioRepository;
import com.lacasablanca.backend.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("PE-9 Mockito 1: Credenciales válidas retorna LoginResponse con token y objeto usuario anidado")
    void autenticarCredencialesValidas() {
        Rol rolMozo = new Rol(2, "MOZO");
        Usuario usuario = new Usuario(10, "mozo_01", "$2a$10$hashValido", rolMozo);

        when(usuarioRepository.findByUsername("mozo_01")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("password123", "$2a$10$hashValido")).thenReturn(true);
        when(jwtUtil.generarToken(10, "mozo_01", "MOZO")).thenReturn("jwt.token.simulado");

        LoginRequest request = new LoginRequest("mozo_01", "password123");
        LoginResponse response = authService.autenticar(request);

        assertNotNull(response);
        assertEquals("jwt.token.simulado", response.getToken());
        assertEquals("Bearer", response.getTipo());
        assertNotNull(response.getUsuario());
        assertEquals(10, response.getUsuario().getId());
        assertEquals("mozo_01", response.getUsuario().getNombre());
        assertEquals("MOZO", response.getUsuario().getRol());
    }

    @Test
    @DisplayName("PE-9 Mockito 2: Contraseña incorrecta lanza BadCredentialsException con mensaje genérico")
    void autenticarClaveIncorrectaLanzaBadCredentials() {
        Rol rolMozo = new Rol(2, "MOZO");
        Usuario usuario = new Usuario(10, "mozo_01", "$2a$10$hashValido", rolMozo);

        when(usuarioRepository.findByUsername("mozo_01")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("clave_erronea", "$2a$10$hashValido")).thenReturn(false);

        LoginRequest request = new LoginRequest("mozo_01", "clave_erronea");
        BadCredentialsException ex = assertThrows(
                BadCredentialsException.class,
                () -> authService.autenticar(request)
        );
        assertEquals("Usuario o contraseña incorrectos", ex.getMessage());
        verifyNoInteractions(jwtUtil);
    }

    @Test
    @DisplayName("PE-9 Mockito 3: Usuario inexistente lanza BadCredentialsException con el mismo mensaje genérico")
    void autenticarUsuarioInexistenteLanzaBadCredentials() {
        when(usuarioRepository.findByUsername("no_existe")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest("no_existe", "password123");
        BadCredentialsException ex = assertThrows(
                BadCredentialsException.class,
                () -> authService.autenticar(request)
        );
        assertEquals("Usuario o contraseña incorrectos", ex.getMessage());
        verifyNoInteractions(passwordEncoder, jwtUtil);
    }
}
