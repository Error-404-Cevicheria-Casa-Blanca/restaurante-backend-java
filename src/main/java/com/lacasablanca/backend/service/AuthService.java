package com.lacasablanca.backend.service;

import com.lacasablanca.backend.dto.LoginRequest;
import com.lacasablanca.backend.dto.LoginResponse;
import com.lacasablanca.backend.dto.PinLoginRequest;
import com.lacasablanca.backend.dto.UsuarioDto;
import com.lacasablanca.backend.exception.CuentaBloqueadaException;
import com.lacasablanca.backend.model.Usuario;
import com.lacasablanca.backend.repository.UsuarioRepository;
import com.lacasablanca.backend.security.JwtUtil;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class AuthService {

    public static final String MENSAJE_CREDENCIALES_INVALIDAS = "Usuario o contraseña incorrectos";
    public static final String MENSAJE_PIN_INVALIDO = "Usuario o PIN incorrectos";
    public static final String MENSAJE_CUENTA_BLOQUEADA =
            "Cuenta bloqueada temporalmente tras 5 intentos fallidos. Intente nuevamente en 15 minutos";

    public static final int MAX_INTENTOS_FALLIDOS = 5;
    public static final int MINUTOS_BLOQUEO = 15;
    private static final Pattern PATRON_PIN = Pattern.compile("^\\d{4}$");

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    private final Map<String, String> pinHashesPorUsuario = new ConcurrentHashMap<>();
    private final Map<String, Integer> intentosFallidosPorUsuario = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> bloqueoHastaPorUsuario = new ConcurrentHashMap<>();

    public AuthService(UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public LoginResponse autenticar(LoginRequest request) {
        String username = request.getUsuario() != null ? request.getUsuario().trim() : "";
        verificarCuentaNoBloqueada(username);

        Optional<Usuario> optUsuario = usuarioRepository.findByUsername(username);
        if (optUsuario.isEmpty() || !passwordEncoder.matches(request.getClave(), optUsuario.get().getClaveHash())) {
            registrarIntentoFallido(username);
            throw new BadCredentialsException(MENSAJE_CREDENCIALES_INVALIDAS);
        }

        limpiarIntentosUsuario(username);
        return construirRespuestaLogin(optUsuario.get());
    }

    public LoginResponse autenticarPorPin(PinLoginRequest request) {
        String username = request.getUsuario() != null ? request.getUsuario().trim() : "";
        String pin = request.getPin() != null ? request.getPin().trim() : "";

        if (!PATRON_PIN.matcher(pin).matches()) {
            throw new IllegalArgumentException("El PIN debe contener exactamente 4 dígitos numéricos");
        }

        verificarCuentaNoBloqueada(username);

        Optional<Usuario> optUsuario = usuarioRepository.findByUsername(username);
        if (optUsuario.isEmpty()) {
            registrarIntentoFallido(username);
            throw new BadCredentialsException(MENSAJE_PIN_INVALIDO);
        }

        Usuario u = optUsuario.get();
        String hashPin = obtenerHashPinDeUsuario(u);
        if (hashPin == null || !passwordEncoder.matches(pin, hashPin)) {
            registrarIntentoFallido(username);
            throw new BadCredentialsException(MENSAJE_PIN_INVALIDO);
        }

        limpiarIntentosUsuario(username);
        return construirRespuestaLogin(u);
    }

    public void registrarPin(String username, String pinPlano) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("El usuario es obligatorio para registrar PIN");
        }
        if (pinPlano == null || !PATRON_PIN.matcher(pinPlano.trim()).matches()) {
            throw new IllegalArgumentException("El PIN debe contener exactamente 4 dígitos numéricos");
        }
        String key = normalizarUsuario(username);
        pinHashesPorUsuario.put(key, passwordEncoder.encode(pinPlano.trim()));
    }

    public void cerrarSesion(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring(7).trim();
            jwtUtil.revocarToken(token);
        }
    }

    public boolean estaBloqueado(String username) {
        String key = normalizarUsuario(username);
        LocalDateTime hasta = bloqueoHastaPorUsuario.get(key);
        if (hasta == null) {
            return false;
        }
        if (LocalDateTime.now().isAfter(hasta)) {
            bloqueoHastaPorUsuario.remove(key);
            intentosFallidosPorUsuario.remove(key);
            return false;
        }
        return true;
    }

    public int obtenerIntentosFallidos(String username) {
        return intentosFallidosPorUsuario.getOrDefault(normalizarUsuario(username), 0);
    }

    public void limpiarBloqueos() {
        intentosFallidosPorUsuario.clear();
        bloqueoHastaPorUsuario.clear();
    }

    private void verificarCuentaNoBloqueada(String username) {
        if (estaBloqueado(username)) {
            throw new CuentaBloqueadaException(MENSAJE_CUENTA_BLOQUEADA);
        }
    }

    private void registrarIntentoFallido(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        String key = normalizarUsuario(username);
        int nuevosIntentos = intentosFallidosPorUsuario.getOrDefault(key, 0) + 1;
        intentosFallidosPorUsuario.put(key, nuevosIntentos);
        if (nuevosIntentos >= MAX_INTENTOS_FALLIDOS) {
            bloqueoHastaPorUsuario.put(key, LocalDateTime.now().plusMinutes(MINUTOS_BLOQUEO));
        }
    }

    private void limpiarIntentosUsuario(String username) {
        String key = normalizarUsuario(username);
        intentosFallidosPorUsuario.remove(key);
        bloqueoHastaPorUsuario.remove(key);
    }

    private String obtenerHashPinDeUsuario(Usuario usuario) {
        String key = normalizarUsuario(usuario.getUsername());
        String registrado = pinHashesPorUsuario.get(key);
        if (registrado != null) {
            return registrado;
        }
        if (usuario.getPinHash() != null && !usuario.getPinHash().isBlank()) {
            return usuario.getPinHash();
        }
        return usuario.getClaveHash();
    }

    private LoginResponse construirRespuestaLogin(Usuario u) {
        String rolNombre = u.getRol().getNombre();
        String token = jwtUtil.generarToken(u.getId(), u.getUsername(), rolNombre);
        UsuarioDto usuarioDto = new UsuarioDto(u.getId(), u.getUsername(), rolNombre);
        return new LoginResponse(token, usuarioDto);
    }

    private String normalizarUsuario(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
