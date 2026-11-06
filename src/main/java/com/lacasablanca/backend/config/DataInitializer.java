package com.lacasablanca.backend.config;

import com.lacasablanca.backend.model.Categoria;
import com.lacasablanca.backend.model.EstadoMesa;
import com.lacasablanca.backend.model.Mesa;
import com.lacasablanca.backend.model.Producto;
import com.lacasablanca.backend.model.Rol;
import com.lacasablanca.backend.model.Usuario;
import com.lacasablanca.backend.repository.CategoriaRepository;
import com.lacasablanca.backend.repository.MesaRepository;
import com.lacasablanca.backend.repository.ProductoRepository;
import com.lacasablanca.backend.repository.RolRepository;
import com.lacasablanca.backend.repository.UsuarioRepository;
import com.lacasablanca.backend.service.AuthService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Inicializador exclusivo para el perfil de pruebas automatizadas (@Profile("test")).
 * En producción este componente NO se carga; los usuarios y PINs reales se administran en base de datos.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final RolRepository rolRepository;
    private final UsuarioRepository usuarioRepository;
    private final MesaRepository mesaRepository;
    private final CategoriaRepository categoriaRepository;
    private final ProductoRepository productoRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public DataInitializer(RolRepository rolRepository,
                           UsuarioRepository usuarioRepository,
                           MesaRepository mesaRepository,
                           CategoriaRepository categoriaRepository,
                           ProductoRepository productoRepository,
                           PasswordEncoder passwordEncoder,
                           AuthService authService) {
        this.rolRepository = rolRepository;
        this.usuarioRepository = usuarioRepository;
        this.mesaRepository = mesaRepository;
        this.categoriaRepository = categoriaRepository;
        this.productoRepository = productoRepository;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
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

        authService.registrarPin("admin_test", "9999");
        authService.registrarPin("mozo_01", "1234");
        authService.registrarPin("mozo_02", "2345");
        authService.registrarPin("cocinero_01", "3456");
        authService.registrarPin("cajero_01", "4567");

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

            productoRepository.save(new Producto(
                    "Ceviche Clásico",
                    "Pescado fresco marinado en limón",
                    new BigDecimal("48.00"),
                    true,
                    cFondos,
                    "https://lacasablanca.pe/ceviche.jpg"));
            productoRepository.save(new Producto(
                    "Chicharrón de Calamar",
                    "Crujientes aros de calamar",
                    new BigDecimal("35.00"),
                    true,
                    cEntradas,
                    "https://lacasablanca.pe/chicharron.jpg"));
            productoRepository.save(new Producto(
                    "Chicha Morada 1L",
                    "Bebida tradicional de maíz morado",
                    new BigDecimal("15.00"),
                    true,
                    cBebidas,
                    "https://lacasablanca.pe/chicha.jpg"));
            productoRepository.save(new Producto(
                    "Suspiro a la Limeña",
                    "Postre tradicional",
                    new BigDecimal("18.00"),
                    false,
                    cPostres,
                    "https://lacasablanca.pe/suspiro.jpg"));
        }
    }
}
