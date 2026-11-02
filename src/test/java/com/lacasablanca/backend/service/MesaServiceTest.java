package com.lacasablanca.backend.service;

import com.lacasablanca.backend.dto.MesaDto;
import com.lacasablanca.backend.exception.ConflictoOperacionException;
import com.lacasablanca.backend.exception.RecursoNoEncontradoException;
import com.lacasablanca.backend.model.EstadoMesa;
import com.lacasablanca.backend.model.Mesa;
import com.lacasablanca.backend.model.Rol;
import com.lacasablanca.backend.model.Usuario;
import com.lacasablanca.backend.repository.MesaRepository;
import com.lacasablanca.backend.repository.UsuarioRepository;
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

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private MesaService mesaService;

    @Test
    @DisplayName("PRB-40 Flujo Valido 1: Transición de estados LIBRE -> OCUPADA -> CUENTA_SOLICITADA")
    void transicionEstadosLibreOcupadaCuentaSolicitada() {
        Mesa mesa = new Mesa(1, 1, 4, "Primer piso", EstadoMesa.LIBRE);
        Usuario mozo = new Usuario(1, "mozo_01", "hash", new Rol("MOZO"));

        when(mesaRepository.findById(1)).thenReturn(Optional.of(mesa));
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(mozo));
        when(mesaRepository.actualizarEstadoCondicional(eq(1), eq(EstadoMesa.OCUPADA), eq(EstadoMesa.LIBRE))).thenReturn(1);
        when(mesaRepository.actualizarEstadoCondicional(eq(1), eq(EstadoMesa.CUENTA_SOLICITADA), eq(EstadoMesa.OCUPADA))).thenReturn(1);

        // 1. LIBRE -> OCUPADA
        MesaDto paso1 = mesaService.actualizarEstado(1, EstadoMesa.OCUPADA, "1");
        assertEquals(EstadoMesa.OCUPADA, paso1.getEstado());
        assertNotNull(mesa.getFechaApertura());
        assertEquals(mozo, mesa.getMozo());

        // 2. OCUPADA -> CUENTA_SOLICITADA
        MesaDto paso2 = mesaService.actualizarEstado(1, EstadoMesa.CUENTA_SOLICITADA, "1");
        assertEquals(EstadoMesa.CUENTA_SOLICITADA, paso2.getEstado());
    }

    @Test
    @DisplayName("PRB-40 Flujo Valido 2: Transición LIBRE -> ATENDIDA (apertura comanda) -> LIBRE (cancelacion PRB-39)")
    void transicionLibreAtendidaLibre() {
        Mesa mesa = new Mesa(2, 2, 4, "Primer piso", EstadoMesa.LIBRE);
        Usuario mozo = new Usuario(1, "mozo_01", "hash", new Rol("MOZO"));

        when(mesaRepository.findById(2)).thenReturn(Optional.of(mesa));
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(mozo));
        when(mesaRepository.actualizarEstadoCondicional(eq(2), eq(EstadoMesa.ATENDIDA), eq(EstadoMesa.LIBRE))).thenReturn(1);
        when(mesaRepository.actualizarEstadoCondicional(eq(2), eq(EstadoMesa.LIBRE), eq(EstadoMesa.ATENDIDA))).thenReturn(1);

        MesaDto atendida = mesaService.actualizarEstado(2, EstadoMesa.ATENDIDA, "1");
        assertEquals(EstadoMesa.ATENDIDA, atendida.getEstado());
        assertNotNull(mesa.getFechaApertura());
        assertNull(mesa.getFechaCierre());
        assertEquals(mozo, mesa.getMozo());

        MesaDto liberada = mesaService.actualizarEstado(2, EstadoMesa.LIBRE, "1");
        assertEquals(EstadoMesa.LIBRE, liberada.getEstado());
        assertNotNull(mesa.getFechaCierre());
    }

    @Test
    @DisplayName("PRB-40 Idempotencia: LIBRE -> LIBRE devuelve 200 y no llama al UPDATE condicional")
    void mismoEstadoLibreEsIdempotente() {
        Mesa mesa = new Mesa(1, 1, 4, "Primer piso", EstadoMesa.LIBRE);
        when(mesaRepository.findById(1)).thenReturn(Optional.of(mesa));

        MesaDto res = mesaService.actualizarEstado(1, EstadoMesa.LIBRE, "1");
        assertEquals(EstadoMesa.LIBRE, res.getEstado());
        verify(mesaRepository, never()).actualizarEstadoCondicional(any(), any(), any());
    }

    @Test
    @DisplayName("PRB-40 Conflicto 409: Transición inválida LIBRE -> CUENTA_SOLICITADA lanza ConflictoOperacionException")
    void transicionInvalidaLanzaConflicto() {
        Mesa mesa = new Mesa(3, 3, 4, "Primer piso", EstadoMesa.LIBRE);
        when(mesaRepository.findById(3)).thenReturn(Optional.of(mesa));

        ConflictoOperacionException ex = assertThrows(
                ConflictoOperacionException.class,
                () -> mesaService.actualizarEstado(3, EstadoMesa.CUENTA_SOLICITADA, "1")
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
                () -> mesaService.actualizarEstado(4, EstadoMesa.ATENDIDA, "1")
        );
        assertTrue(ex.getMessage().contains("no está disponible"));
        verify(mesaRepository, never()).actualizarEstadoCondicional(any(), any(), any());
    }

    @Test
    @DisplayName("PRB-40 Conflicto 409: Mismo estado OCUPADA lanza ConflictoOperacionException")
    void mismoEstadoLanzaConflicto() {
        Mesa mesa = new Mesa(5, 5, 4, "Primer piso", EstadoMesa.OCUPADA);
        when(mesaRepository.findById(5)).thenReturn(Optional.of(mesa));

        ConflictoOperacionException ex = assertThrows(
                ConflictoOperacionException.class,
                () -> mesaService.actualizarEstado(5, EstadoMesa.OCUPADA, "1")
        );
        assertTrue(ex.getMessage().contains("ya se encuentra en estado OCUPADA"));
        verify(mesaRepository, never()).actualizarEstadoCondicional(any(), any(), any());
    }

    @Test
    @DisplayName("PRB-40 Bloqueo Optimista: Carrera de concurrencia (0 filas actualizadas) lanza ConflictoOperacionException 409 con estado fresco")
    void carreraConcurrenciaOptimistaLanzaConflicto() {
        Mesa mesa = new Mesa(6, 6, 4, "Primer piso", EstadoMesa.LIBRE);
        Usuario mozo = new Usuario(1, "mozo_01", "hash", new Rol("MOZO"));

        when(mesaRepository.findById(6)).thenReturn(Optional.of(mesa));
        when(usuarioRepository.findById(1)).thenReturn(Optional.of(mozo));
        // Otra transacción ganó la carrera y cambió el estado antes
        when(mesaRepository.actualizarEstadoCondicional(eq(6), eq(EstadoMesa.ATENDIDA), eq(EstadoMesa.LIBRE))).thenReturn(0);
        when(mesaRepository.findEstadoById(6)).thenReturn(Optional.of(EstadoMesa.ATENDIDA));

        ConflictoOperacionException ex = assertThrows(
                ConflictoOperacionException.class,
                () -> mesaService.actualizarEstado(6, EstadoMesa.ATENDIDA, "1")
        );
        assertTrue(ex.getMessage().contains("Conflicto de concurrencia"));
        assertTrue(ex.getMessage().contains("ATENDIDA"));
    }

    @Test
    @DisplayName("PE-10 Mockito 2: Mesa inexistente lanza RecursoNoEncontradoException (404)")
    void actualizarEstadoMesaInexistente() {
        when(mesaRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(
                RecursoNoEncontradoException.class,
                () -> mesaService.actualizarEstado(999, EstadoMesa.OCUPADA, "1")
        );
    }
}
