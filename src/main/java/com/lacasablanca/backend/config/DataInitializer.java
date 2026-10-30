package com.lacasablanca.backend.config;

import com.lacasablanca.backend.model.*;
import com.lacasablanca.backend.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Profile("test")
public class DataInitializer implements CommandLineRunner {

    private final RolRepository rolRepository;
    private final UsuarioRepository usuarioRepository;
    private final MesaRepository mesaRepository;
    private final CategoriaRepository categoriaRepository;
    private final ProductoRepository productoRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(RolRepository rolRepository,
                           UsuarioRepository usuarioRepository,
                           MesaRepository mesaRepository,
                           CategoriaRepository categoriaRepository,
                           ProductoRepository productoRepository,
                           PasswordEncoder passwordEncoder) {
        this.rolRepository = rolRepository;
        this.usuarioRepository = usuarioRepository;
        this.mesaRepository = mesaRepository;
        this.categoriaRepository = categoriaRepository;
        this.productoRepository = productoRepository;
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

        if (mesaRepository.count() == 0) {
            for (int i = 1; i <= 7; i++) {
                mesaRepository.save(new Mesa(i, 4, "Primer piso", EstadoMesa.LIBRE));
            }
            for (int i = 8; i <= 13; i++) {
                mesaRepository.save(new Mesa(i, 4, "Segundo piso", EstadoMesa.LIBRE));
            }
        }

        if (categoriaRepository.count() == 0) {
            Categoria cEntradas = categoriaRepository.save(new Categoria("Entradas"));
            Categoria cFondos = categoriaRepository.save(new Categoria("Platos de Fondo"));
            Categoria cBebidas = categoriaRepository.save(new Categoria("Bebidas"));
            Categoria cPostres = categoriaRepository.save(new Categoria("Postres"));

            productoRepository.save(new Producto("Ceviche Clásico", "Pescado fresco marinado en limón", new BigDecimal("48.00"), true, cFondos, "https://lacasablanca.pe/ceviche.jpg"));
            productoRepository.save(new Producto("Chicharrón de Calamar", "Crujientes aros de calamar", new BigDecimal("35.00"), true, cEntradas, "https://lacasablanca.pe/chicharron.jpg"));
            productoRepository.save(new Producto("Chicha Morada 1L", "Bebida tradicional de maíz morado", new BigDecimal("15.00"), true, cBebidas, "https://lacasablanca.pe/chicha.jpg"));
            productoRepository.save(new Producto("Suspiro a la Limeña", "Postre tradicional", new BigDecimal("18.00"), false, cPostres, "https://lacasablanca.pe/suspiro.jpg")); // No disponible
        }
    }
}
