package com.lacasablanca.backend;

import com.lacasablanca.backend.exception.ConflictoOperacionException;
import com.lacasablanca.backend.model.EstadoMesa;
import com.lacasablanca.backend.model.Mesa;
import com.lacasablanca.backend.repository.MesaRepository;
import com.lacasablanca.backend.service.MesaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class MesaConcurrenciaTest {

    @Autowired private MesaService mesaService;
    @Autowired private MesaRepository mesaRepository;

    private Integer mesaId;

    @BeforeEach
    void preparar() {
        Mesa m = new Mesa(1000, 4, "Test concurrencia", EstadoMesa.LIBRE);
        mesaId = mesaRepository.save(m).getId();
    }

    @AfterEach
    void limpiar() {
        if (mesaId != null) {
            try {
                mesaRepository.deleteById(mesaId);
            } catch (Exception ignored) {}
        }
    }

    @Test
    @DisplayName("PRB-40 Concurrencia Real: Dos aperturas simultáneas sobre la misma mesa libre: exactamente una gana y la otra recibe 409")
    void dosAperturasConcurrentesSoloUnaGana() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch listos = new CountDownLatch(2);
        CountDownLatch salida = new CountDownLatch(1);

        Callable<String> intento = () -> {
            listos.countDown();
            salida.await(5, TimeUnit.SECONDS);
            try {
                mesaService.actualizarEstado(mesaId, EstadoMesa.ATENDIDA, "2");
                return "OK";
            } catch (ConflictoOperacionException e) {
                return "409";
            }
        };

        Future<String> f1 = pool.submit(intento);
        Future<String> f2 = pool.submit(intento);
        listos.await(5, TimeUnit.SECONDS);
        salida.countDown();

        List<String> resultados = List.of(
                f1.get(10, TimeUnit.SECONDS),
                f2.get(10, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertEquals(1, Collections.frequency(resultados, "OK"), "Exactamente una apertura debió retornar OK");
        assertEquals(1, Collections.frequency(resultados, "409"), "Exactamente una apertura debió fallar con 409");
        assertEquals(EstadoMesa.ATENDIDA,
                mesaRepository.findById(mesaId).orElseThrow().getEstado());
    }
}
