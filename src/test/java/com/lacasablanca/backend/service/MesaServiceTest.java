package com.lacasablanca.backend.service;

import com.lacasablanca.backend.dto.MesaDto;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MesaServiceTest {

    @Mock
    private MesaRepository mesaRepository;

    @InjectMocks
    private MesaService mesaService;

    @Test
    @DisplayName("PE-10 Mockito 1: Transición de estados LIBRE -> OCUPADA -> CUENTA_SOLICITADA")
    void transicionEstadosLibreOcupadaCuentaSolicitada() {
        Mesa mesa = new Mesa(1, 1, 4, "Primer piso", EstadoMesa.LIBRE);
        when(mesaRepository.findById(1)).thenReturn(Optional.of(mesa));
        when(mesaRepository.save(any(Mesa.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1. LIBRE -> OCUPADA
        MesaDto paso1 = mesaService.actualizarEstado(1, EstadoMesa.OCUPADA);
        assertEquals(EstadoMesa.OCUPADA, paso1.getEstado());

        // 2. OCUPADA -> CUENTA_SOLICITADA
        MesaDto paso2 = mesaService.actualizarEstado(1, EstadoMesa.CUENTA_SOLICITADA);
        assertEquals(EstadoMesa.CUENTA_SOLICITADA, paso2.getEstado());

        verify(mesaRepository, times(2)).save(mesa);
    }

    @Test
    @DisplayName("PE-10 Mockito 2: Mesa inexistente lanza RecursoNoEncontradoException")
    void actualizarEstadoMesaInexistente() {
        when(mesaRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(
                RecursoNoEncontradoException.class,
                () -> mesaService.actualizarEstado(999, EstadoMesa.OCUPADA)
        );
    }
}
