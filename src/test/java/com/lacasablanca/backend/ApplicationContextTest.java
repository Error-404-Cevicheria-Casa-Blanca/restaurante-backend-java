package com.lacasablanca.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifica que el contexto de Spring arranca con el perfil "test" (H2 en memoria).
 * Es la prueba de humo del esqueleto de PE-6.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextTest {

    @Test
    void contextLoads() {
    }
}
