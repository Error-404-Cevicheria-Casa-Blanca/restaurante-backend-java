package com.lacasablanca.backend.service;

import com.lacasablanca.backend.dto.MesaDto;
import com.lacasablanca.backend.exception.ConflictoOperacionException;
import com.lacasablanca.backend.exception.RecursoNoEncontradoException;
import com.lacasablanca.backend.model.EstadoMesa;
import com.lacasablanca.backend.model.Mesa;
import com.lacasablanca.backend.repository.MesaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MesaServiceTest {

    @Mock
    private MesaRepository mesaRepository;

    @InjectMocks
    private MesaService mesaService;

    @Test
    @DisplayName("PRB-40 Flujo Valido 1: Transición de estados LIBRE -> OCUPADA -> CUENTA_SOLICITADA")
    void transicionEstadosLibreOcupadaCuentaSolicitada() {
        Mesa mesa = new Mesa(1, 1, 4, "Primer piso", EstadoMesa.LIBRE);
        when(mesaRepository.findById(1)).thenReturn(Optional.of(mesa));
        when(mesaRepository.actualizarEstadoCondicional(eq(1), eq(EstadoMesa.OCUPADA), eq(EstadoMesa.LIBRE))).thenReturn(1);
        when(mesaRepository.actualizarEstadoCondicional(eq(1), eq(EstadoMesa.CUENTA_SOLICITADA), eq(EstadoMesa.OCUPADA))).thenReturn(1);

        // 1. LIBRE -> OCUPADA
        MesaDto paso1 = mesaService.actualizarEstado(1, EstadoMesa.OCUPADA);
        assertEquals(EstadoMesa.OCUPADA, paso1.getEstado());

        // 2. OCUPADA -> CUENTA_SOLICITADA
        MesaDto paso2 = mesaService.actualizarEstado(1, EstadoMesa.CUENTA_SOLICITADA);
        assertEquals(EstadoMesa.CUENTA_SOLICITADA, paso2.getEstado());
    }

    @Test
    @DisplayName("PRB-40 Flujo Valido 2: Transición LIBRE -> ATENDIDA (apertura comanda) -> LIBRE (cancelacion PRB-39)")
    void transicionLibreAtendidaLibre() {
        Mesa mesa = new Mesa(2, 2, 4, "Primer piso", EstadoMesa.LIBRE);
        when(mesaRepository.findById(2)).thenReturn(Optional.of(mesa));
        when(mesaRepository.actualizarEstadoCondicional(eq(2), eq(EstadoMesa.ATENDIDA), eq(EstadoMesa.LIBRE))).thenReturn(1);
        when(mesaRepository.actualizarEstadoCondicional(eq(2), eq(EstadoMesa.LIBRE), eq(EstadoMesa.ATENDIDA))).thenReturn(1);

        MesaDto atendida = mesaService.actualizarEstado(2, EstadoMesa.ATENDIDA);
        assertEquals(EstadoMesa.ATENDIDA, atendida.getEstado());

        MesaDto liberada = mesaService.actualizarEstado(2, EstadoMesa.LIBRE);
        assertEquals(EstadoMesa.LIBRE, liberada.getEstado());
    }

    @Test
    @DisplayName("PRB-40 Conflicto 409: Transición inválida LIBRE -> CUENTA_SOLICITADA lanza ConflictoOperacionException")
    void transicionInvalidaLanzaConflicto() {
        Mesa mesa = new Mesa(3, 3, 4, "Primer piso", EstadoMesa.LIBRE);
        when(mesaRepository.findById(3)).thenReturn(Optional.of(mesa));

        ConflictoOperacionException ex = assertThrows(
                ConflictoOperacionException.class,
                () -> mesaService.actualizarEstado(3, EstadoMesa.CUENTA_SOLICITADA)
        );
        assertTrue(ex.getMessage().contains("Transición de estado no permitida"));
        verify(mesaRepository, never()).actualizarEstadoCondicional(any(), any(), any());
    }

    @Test
    @DisplayName("PRB-40 Conflicto 409: Intentar abrir mesa ya OCUPADA lanza ConflictoOperacionException")
    void intentarAbrirMesaOcupadaLanzaConflicto() {
        Mesa mesa = new Mesa(4, 4, 4, "Primer piso", EstadoMesa.OCUPADA);
        when(mesaRepository.findById(4)).thenReturn(Optional.of(mesa));

        ConflictoOperacionException ex = assertThrows(
                ConflictoOperacionException.class,
                () -> mesaService.actualizarEstado(4, EstadoMesa.ATENDIDA)
        );
        assertTrue(ex.getMessage().contains("no está disponible"));
        verify(mesaRepository, never()).actualizarEstadoCondicional(any(), any(), any());
    }

    @Test
    @DisplayName("PRB-40 Conflicto 409: Mismo estado lanza ConflictoOperacionException")
    void mismoEstadoLanzaConflicto() {
        Mesa mesa = new Mesa(5, 5, 4, "Primer piso", EstadoMesa.OCUPADA);
        when(mesaRepository.findById(5)).thenReturn(Optional.of(mesa));

        ConflictoOperacionException ex = assertThrows(
                ConflictoOperacionException.class,
                () -> mesaService.actualizarEstado(5, EstadoMesa.OCUPADA)
        );
        assertTrue(ex.getMessage().contains("ya se encuentra en estado OCUPADA"));
        verify(mesaRepository, never()).actualizarEstadoCondicional(any(), any(), any());
    }

    @Test
    @DisplayName("PRB-40 Bloqueo Optimista: Carrera de concurrencia (0 filas actualizadas) lanza ConflictoOperacionException 409")
    void carreraConcurrenciaOptimistaLanzaConflicto() {
        Mesa mesa = new Mesa(6, 6, 4, "Primer piso", EstadoMesa.LIBRE);
        when(mesaRepository.findById(6)).thenReturn(Optional.of(mesa));
        // Otra transacción ganó la carrera y cambió el estado antes
        when(mesaRepository.actualizarEstadoCondicional(eq(6), eq(EstadoMesa.ATENDIDA), eq(EstadoMesa.LIBRE))).thenReturn(0);

        ConflictoOperacionException ex = assertThrows(
                ConflictoOperacionException.class,
                () -> mesaService.actualizarEstado(6, EstadoMesa.ATENDIDA)
        );
        assertTrue(ex.getMessage().contains("Conflicto de concurrencia"));
    }

    @Test
    @DisplayName("PE-10 Mockito 2: Mesa inexistente lanza RecursoNoEncontradoException (404)")
    void actualizarEstadoMesaInexistente() {
        when(mesaRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(
                RecursoNoEncontradoException.class,
                () -> mesaService.actualizarEstado(999, EstadoMesa.OCUPADA)
        );
    }
}
