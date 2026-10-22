package com.lacasablanca.backend.service;

import com.lacasablanca.backend.dto.LoginRequest;
import com.lacasablanca.backend.dto.LoginResponse;
import com.lacasablanca.backend.dto.UsuarioDto;
import com.lacasablanca.backend.model.Usuario;
import com.lacasablanca.backend.repository.UsuarioRepository;
import com.lacasablanca.backend.security.JwtUtil;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService {

    public static final String MENSAJE_CREDENCIALES_INVALIDAS = "Usuario o contraseña incorrectos";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public LoginResponse autenticar(LoginRequest request) {
        Optional<Usuario> optUsuario = usuarioRepository.findByUsername(request.getUsuario());
        if (optUsuario.isEmpty() || !passwordEncoder.matches(request.getClave(), optUsuario.get().getClaveHash())) {
            throw new BadCredentialsException(MENSAJE_CREDENCIALES_INVALIDAS);
        }
        Usuario u = optUsuario.get();
        String rolNombre = u.getRol().getNombre();
        String token = jwtUtil.generarToken(u.getId(), u.getUsername(), rolNombre);
        UsuarioDto usuarioDto = new UsuarioDto(u.getId(), u.getUsername(), rolNombre);
        return new LoginResponse(token, usuarioDto);
    }
}
