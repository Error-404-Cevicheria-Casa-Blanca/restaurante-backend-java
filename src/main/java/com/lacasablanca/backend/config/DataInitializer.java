package com.lacasablanca.backend.config;

import com.lacasablanca.backend.model.Rol;
import com.lacasablanca.backend.model.Usuario;
import com.lacasablanca.backend.repository.RolRepository;
import com.lacasablanca.backend.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class DataInitializer implements CommandLineRunner {

    private final RolRepository rolRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(RolRepository rolRepository,
                           UsuarioRepository usuarioRepository,
                           PasswordEncoder passwordEncoder) {
        this.rolRepository = rolRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (rolRepository.count() == 0) {
            Rol rAdmin = rolRepository.save(new Rol("ADMIN"));
            Rol rMozo = rolRepository.save(new Rol("MOZO"));
            Rol rCocinero = rolRepository.save(new Rol("COCINERO"));
            Rol rCajero = rolRepository.save(new Rol("CAJERO"));

            String hash = passwordEncoder.encode("password123");
            usuarioRepository.save(new Usuario("admin_test", hash, rAdmin));
            usuarioRepository.save(new Usuario("mozo_01", hash, rMozo));
            usuarioRepository.save(new Usuario("mozo_02", hash, rMozo));
            usuarioRepository.save(new Usuario("cocinero_01", hash, rCocinero));
            usuarioRepository.save(new Usuario("cajero_01", hash, rCajero));
        }
    }
}
