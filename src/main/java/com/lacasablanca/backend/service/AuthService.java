package com.lacasablanca.backend.service;

import com.lacasablanca.backend.dto.LoginRequest;
import com.lacasablanca.backend.dto.LoginResponse;
import com.lacasablanca.backend.dto.PinLoginRequest;
import com.lacasablanca.backend.dto.TerminalDto;
import com.lacasablanca.backend.dto.TurnoDto;
import com.lacasablanca.backend.dto.UsuarioDto;
import com.lacasablanca.backend.exception.CuentaBloqueadaException;
import com.lacasablanca.backend.model.SesionMozo;
import com.lacasablanca.backend.model.Terminal;
import com.lacasablanca.backend.model.Turno;
import com.lacasablanca.backend.model.Usuario;
import com.lacasablanca.backend.repository.SesionMozoRepository;
import com.lacasablanca.backend.repository.TerminalRepository;
import com.lacasablanca.backend.repository.TurnoRepository;
import com.lacasablanca.backend.repository.UsuarioRepository;
import com.lacasablanca.backend.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
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
    public static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");
    private static final Pattern PATRON_PIN = Pattern.compile("^\\d{4}$");

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final TurnoRepository turnoRepository;
    private final TerminalRepository terminalRepository;
    private final SesionMozoRepository sesionMozoRepository;

    private final Map<String, String> pinHashesPorUsuario = new ConcurrentHashMap<>();
    private final Map<String, Integer> intentosFallidosPorUsuario = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> bloqueoHastaPorUsuario = new ConcurrentHashMap<>();

    @Autowired
    public AuthService(UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       TurnoRepository turnoRepository,
                       TerminalRepository terminalRepository,
                       SesionMozoRepository sesionMozoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.turnoRepository = turnoRepository;
        this.terminalRepository = terminalRepository;
        this.sesionMozoRepository = sesionMozoRepository;
    }

    public AuthService(UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this(usuarioRepository, passwordEncoder, jwtUtil, null, null, null);
    }

    public LoginResponse autenticar(LoginRequest request) {
        String username = request.getUsuario() != null ? request.getUsuario().trim() : "";
        verificarCuentaNoBloqueada(username);

        Optional<Usuario> optUsuario = usuarioRepository.findByUsername(username);
        if (optUsuario.isEmpty()) {
            throw new BadCredentialsException(MENSAJE_CREDENCIALES_INVALIDAS);
        }

        Usuario u = optUsuario.get();
        if (estaBloqueado(u.getUsername())) {
            throw new CuentaBloqueadaException(MENSAJE_CUENTA_BLOQUEADA);
        }

        if (!passwordEncoder.matches(request.getClave(), u.getClaveHash())) {
            registrarIntentoFallido(u);
            throw new BadCredentialsException(MENSAJE_CREDENCIALES_INVALIDAS);
        }

        limpiarIntentosUsuario(u);
        return construirRespuestaLogin(u, request.getTerminalCodigo());
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
            throw new BadCredentialsException(MENSAJE_PIN_INVALIDO);
        }

        Usuario u = optUsuario.get();
        if (estaBloqueado(u.getUsername())) {
            throw new CuentaBloqueadaException(MENSAJE_CUENTA_BLOQUEADA);
        }

        String hashPin = obtenerHashPinDeUsuario(u);
        if (hashPin == null || !passwordEncoder.matches(pin, hashPin)) {
            registrarIntentoFallido(u);
            throw new BadCredentialsException(MENSAJE_PIN_INVALIDO);
        }

        limpiarIntentosUsuario(u);
        return construirRespuestaLogin(u, request.getTerminalCodigo());
    }

    public Map<String, Object> obtenerPerfilActual(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new BadCredentialsException("No autenticado");
        }

        String subject = authentication.getName();
        Integer usuarioId = null;
        try {
            usuarioId = Integer.valueOf(subject);
        } catch (NumberFormatException ignored) {
            // Continúa buscando por username si el subject no es numérico
        }

        Optional<Usuario> optUsuario;
        if (usuarioId != null) {
            optUsuario = usuarioRepository.findById(usuarioId);
        } else {
            optUsuario = usuarioRepository.findByUsername(subject);
        }

        if (optUsuario.isEmpty()) {
            throw new BadCredentialsException("Usuario no encontrado");
        }

        Usuario u = optUsuario.get();
        String rolNombre = u.getRol().getNombre();

        Terminal terminalAsignada = null;
        Turno turnoAsignado = obtenerTurnoActual();

        if (sesionMozoRepository != null) {
            Optional<SesionMozo> optSesion = sesionMozoRepository
                    .findFirstByUsuarioIdAndFinIsNullOrderByInicioDesc(u.getId());
            if (optSesion.isPresent()) {
                terminalAsignada = optSesion.get().getTerminal();
                if (optSesion.get().getTurno() != null) {
                    turnoAsignado = optSesion.get().getTurno();
                }
            }
        }

        TurnoDto turnoDto = TurnoDto.desdeEntidad(turnoAsignado);
        TerminalDto terminalDto = TerminalDto.desdeEntidad(terminalAsignada);
        String nombreVisible = calcularNombreVisible(u.getUsername(), rolNombre);

        UsuarioDto usuarioDto = new UsuarioDto(
                u.getId(),
                u.getUsername(),
                nombreVisible,
                rolNombre,
                turnoDto,
                terminalDto
        );

        String fechaHoraServidor = obtenerFechaHoraServidor();

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("usuario_id", authentication.getName());
        respuesta.put("roles", authentication.getAuthorities());
        respuesta.put("usuario", usuarioDto);
        respuesta.put("turno", turnoDto);
        respuesta.put("terminal", terminalDto);
        respuesta.put("fecha_hora_servidor", fechaHoraServidor);

        return respuesta;
    }

    public Turno obtenerTurnoActual() {
        if (turnoRepository == null) {
            return null;
        }
        List<Turno> activos = turnoRepository.findByActivoTrue();
        if (activos.isEmpty()) {
            return null;
        }
        LocalTime ahora = LocalTime.now(ZONA_LIMA);
        for (Turno t : activos) {
            if (t.getHoraInicio() != null && t.getHoraFin() != null) {
                if (t.getHoraInicio().isBefore(t.getHoraFin())) {
                    if (!ahora.isBefore(t.getHoraInicio()) && ahora.isBefore(t.getHoraFin())) {
                        return t;
                    }
                } else {
                    if (!ahora.isBefore(t.getHoraInicio()) || ahora.isBefore(t.getHoraFin())) {
                        return t;
                    }
                }
            }
        }
        return activos.get(0);
    }

    public static String calcularNombreVisible(String username, String rol) {
        if (username == null || username.isBlank()) {
            return "Usuario";
        }
        switch (username.toLowerCase(Locale.ROOT)) {
            case "mozo_01":
                return "Mozo Salón (Piso 1)";
            case "mozo_02":
                return "Mozo Salón (Piso 2)";
            case "admin_casablanca":
                return "Administrador Casa Blanca";
            case "admin_test":
                return "Administrador Test";
            case "cajero_01":
                return "Cajero 01";
            case "cocinero_01":
                return "Cocinero 01";
            default:
                String[] parts = username.split("[._-]+");
                StringBuilder sb = new StringBuilder();
                for (String part : parts) {
                    if (!part.isBlank()) {
                        if (sb.length() > 0) {
                            sb.append(" ");
                        }
                        sb.append(Character.toUpperCase(part.charAt(0)));
                        if (part.length() > 1) {
                            sb.append(part.substring(1));
                        }
                    }
                }
                return sb.length() > 0 ? sb.toString() : username;
        }
    }

    public static String obtenerFechaHoraServidor() {
        return OffsetDateTime.now(ZONA_LIMA).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    public void registrarPin(String username, String pinPlano) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("El usuario es obligatorio para registrar PIN");
        }
        if (pinPlano == null || !PATRON_PIN.matcher(pinPlano.trim()).matches()) {
            throw new IllegalArgumentException("El PIN debe contener exactamente 4 dígitos numéricos");
        }
        String key = normalizarUsuario(username);
        String encodedPin = passwordEncoder.encode(pinPlano.trim());
        pinHashesPorUsuario.put(key, encodedPin);

        usuarioRepository.findByUsername(username).ifPresent(u -> {
            u.setPinHash(encodedPin);
            usuarioRepository.save(u);
        });
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
        if (hasta != null) {
            if (LocalDateTime.now().isAfter(hasta)) {
                bloqueoHastaPorUsuario.remove(key);
                intentosFallidosPorUsuario.remove(key);
                usuarioRepository.findByUsername(username).ifPresent(u -> {
                    u.setBloqueadoHasta(null);
                    u.setIntentosFallidos(0);
                    usuarioRepository.save(u);
                });
                return false;
            }
            return true;
        }

        Optional<Usuario> opt = usuarioRepository.findByUsername(username);
        if (opt.isPresent()) {
            Usuario u = opt.get();
            if (u.getBloqueadoHasta() != null) {
                if (LocalDateTime.now().isAfter(u.getBloqueadoHasta())) {
                    u.setBloqueadoHasta(null);
                    u.setIntentosFallidos(0);
                    usuarioRepository.save(u);
                    return false;
                }
                bloqueoHastaPorUsuario.put(key, u.getBloqueadoHasta());
                return true;
            }
        }
        return false;
    }

    public int obtenerIntentosFallidos(String username) {
        Integer mem = intentosFallidosPorUsuario.get(normalizarUsuario(username));
        if (mem != null) {
            return mem;
        }
        return usuarioRepository.findByUsername(username)
                .map(Usuario::getIntentosFallidos)
                .orElse(0);
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

    private void registrarIntentoFallido(Usuario usuario) {
        if (usuario == null) {
            return;
        }
        String key = normalizarUsuario(usuario.getUsername());
        int nuevosIntentos = intentosFallidosPorUsuario.getOrDefault(key, usuario.getIntentosFallidos()) + 1;
        intentosFallidosPorUsuario.put(key, nuevosIntentos);
        usuario.setIntentosFallidos(nuevosIntentos);

        if (nuevosIntentos >= MAX_INTENTOS_FALLIDOS) {
            LocalDateTime hasta = LocalDateTime.now().plusMinutes(MINUTOS_BLOQUEO);
            bloqueoHastaPorUsuario.put(key, hasta);
            usuario.setBloqueadoHasta(hasta);
        }
        usuarioRepository.save(usuario);
    }

    private void limpiarIntentosUsuario(Usuario usuario) {
        if (usuario == null) {
            return;
        }
        String key = normalizarUsuario(usuario.getUsername());
        intentosFallidosPorUsuario.remove(key);
        bloqueoHastaPorUsuario.remove(key);
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
        usuarioRepository.save(usuario);
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
        return null;
    }

    private LoginResponse construirRespuestaLogin(Usuario u, String terminalCodigo) {
        String rolNombre = u.getRol().getNombre();
        String token = jwtUtil.generarToken(u.getId(), u.getUsername(), rolNombre);

        Terminal terminalAsignada = null;
        Turno turnoAsignado = obtenerTurnoActual();

        if (terminalCodigo != null && !terminalCodigo.isBlank()) {
            if (terminalRepository != null) {
                Terminal term = terminalRepository.findByCodigo(terminalCodigo.trim())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Terminal inválida o no registrada: " + terminalCodigo
                        ));
                if (!Boolean.TRUE.equals(term.getActivo())) {
                    throw new IllegalArgumentException("Terminal inactiva: " + terminalCodigo);
                }
                terminalAsignada = term;

                if (sesionMozoRepository != null) {
                    OffsetDateTime ahora = OffsetDateTime.now(ZONA_LIMA);
                    List<SesionMozo> activas = sesionMozoRepository.findByUsuarioIdAndFinIsNull(u.getId());
                    for (SesionMozo s : activas) {
                        s.setFin(ahora);
                        sesionMozoRepository.save(s);
                    }
                    SesionMozo nueva = new SesionMozo(u, term, turnoAsignado, ahora);
                    sesionMozoRepository.save(nueva);
                }
            }
        } else if (sesionMozoRepository != null) {
            Optional<SesionMozo> optSesion = sesionMozoRepository
                    .findFirstByUsuarioIdAndFinIsNullOrderByInicioDesc(u.getId());
            if (optSesion.isPresent()) {
                terminalAsignada = optSesion.get().getTerminal();
                if (optSesion.get().getTurno() != null) {
                    turnoAsignado = optSesion.get().getTurno();
                }
            }
        }

        TurnoDto turnoDto = TurnoDto.desdeEntidad(turnoAsignado);
        TerminalDto terminalDto = TerminalDto.desdeEntidad(terminalAsignada);
        String nombreVisible = calcularNombreVisible(u.getUsername(), rolNombre);

        UsuarioDto usuarioDto = new UsuarioDto(
                u.getId(),
                u.getUsername(),
                nombreVisible,
                rolNombre,
                turnoDto,
                terminalDto
        );

        String fechaHoraServidor = obtenerFechaHoraServidor();
        return new LoginResponse(token, usuarioDto, fechaHoraServidor);
    }

    private String normalizarUsuario(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
