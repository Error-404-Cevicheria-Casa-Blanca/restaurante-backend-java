package com.lacasablanca.backend.service;

import com.lacasablanca.backend.dto.LoginRequest;
import com.lacasablanca.backend.dto.LoginResponse;
import com.lacasablanca.backend.model.Rol;
import com.lacasablanca.backend.model.Terminal;
import com.lacasablanca.backend.model.Turno;
import com.lacasablanca.backend.model.Usuario;
import com.lacasablanca.backend.repository.SesionMozoRepository;
import com.lacasablanca.backend.repository.TerminalRepository;
import com.lacasablanca.backend.repository.TurnoRepository;
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

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private TurnoRepository turnoRepository;

    @Mock
    private TerminalRepository terminalRepository;

    @Mock
    private SesionMozoRepository sesionMozoRepository;

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
        assertEquals("Mozo Salón (Piso 1)", response.getUsuario().getNombreVisible());
        assertNotNull(response.getFechaHoraServidor());
    }

    @Test
    @DisplayName("PRB-85: Autenticar con terminal válida registra y retorna terminal y turno")
    void autenticarConTerminalValida() {
        Rol rolMozo = new Rol(2, "MOZO");
        Usuario usuario = new Usuario(10, "mozo_01", "$2a$10$hashValido", rolMozo);
        Terminal terminal = new Terminal(1, "TERM-01", "Tablet Salón Principal", true);
        Turno turno = new Turno(1, "Mañana", LocalTime.of(8, 0), LocalTime.of(16, 0), true);

        when(usuarioRepository.findByUsername("mozo_01")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("password123", "$2a$10$hashValido")).thenReturn(true);
        when(jwtUtil.generarToken(10, "mozo_01", "MOZO")).thenReturn("jwt.token.simulado");
        when(terminalRepository.findByCodigo("TERM-01")).thenReturn(Optional.of(terminal));
        when(turnoRepository.findByActivoTrue()).thenReturn(List.of(turno));

        LoginRequest request = new LoginRequest("mozo_01", "password123", "TERM-01");
        LoginResponse response = authService.autenticar(request);

        assertNotNull(response);
        assertNotNull(response.getUsuario());
        assertNotNull(response.getUsuario().getTerminal());
        assertEquals("TERM-01", response.getUsuario().getTerminal().getCodigo());
        assertEquals("Tablet Salón Principal", response.getUsuario().getTerminal().getNombre());
        assertNotNull(response.getUsuario().getTurno());
        assertEquals("Mañana", response.getUsuario().getTurno().getNombre());
    }

    @Test
    @DisplayName("PRB-85: Autenticar con terminal inexistente lanza IllegalArgumentException")
    void autenticarConTerminalInexistente() {
        Rol rolMozo = new Rol(2, "MOZO");
        Usuario usuario = new Usuario(10, "mozo_01", "$2a$10$hashValido", rolMozo);

        when(usuarioRepository.findByUsername("mozo_01")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("password123", "$2a$10$hashValido")).thenReturn(true);
        when(jwtUtil.generarToken(10, "mozo_01", "MOZO")).thenReturn("jwt.token.simulado");
        when(terminalRepository.findByCodigo("TERM-INVALID")).thenReturn(Optional.empty());

        LoginRequest request = new LoginRequest("mozo_01", "password123", "TERM-INVALID");
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> authService.autenticar(request)
        );
        assertEquals("Terminal inválida o no registrada: TERM-INVALID", ex.getMessage());
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
