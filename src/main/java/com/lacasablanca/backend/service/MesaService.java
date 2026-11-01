package com.lacasablanca.backend.service;

import com.lacasablanca.backend.dto.MesaDto;
import com.lacasablanca.backend.exception.ConflictoOperacionException;
import com.lacasablanca.backend.exception.RecursoNoEncontradoException;
import com.lacasablanca.backend.model.EstadoMesa;
import com.lacasablanca.backend.model.Mesa;
import com.lacasablanca.backend.repository.MesaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MesaService {

    private final MesaRepository mesaRepository;

    public MesaService(MesaRepository mesaRepository) {
        this.mesaRepository = mesaRepository;
    }

    @Transactional(readOnly = true)
    public List<MesaDto> listarMesasOrdenadas() {
        return mesaRepository.findAllByOrderByNumeroAsc().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public MesaDto actualizarEstado(Integer id, EstadoMesa nuevoEstado) {
        if (nuevoEstado == null) {
            throw new IllegalArgumentException("El estado es obligatorio");
        }
        Mesa mesa = mesaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mesa no encontrada con ID: " + id));

        EstadoMesa origen = mesa.getEstado();

        // 1. Conflicto por estado identico (idempotencia rechazada como conflicto si ya esta tomada)
        if (origen == nuevoEstado) {
            throw new ConflictoOperacionException("La mesa " + mesa.getNumero() + " ya se encuentra en estado " + origen);
        }

        // 2. Validacion de la maquina de estados de la mesa (PRB-40)
        if (!esTransicionValida(origen, nuevoEstado)) {
            if ((nuevoEstado == EstadoMesa.ATENDIDA || nuevoEstado == EstadoMesa.OCUPADA) && origen != EstadoMesa.LIBRE) {
                throw new ConflictoOperacionException("La mesa " + mesa.getNumero() + " no está disponible. Estado actual: " + origen);
            }
            throw new ConflictoOperacionException("Transición de estado no permitida: de " + origen + " a " + nuevoEstado + " para la mesa " + mesa.getNumero());
        }

        // 3. Bloqueo optimista atomico a nivel de estado (D7 - DDL Ownership)
        int actualizados = mesaRepository.actualizarEstadoCondicional(id, nuevoEstado, origen);
        if (actualizados == 0) {
            Mesa mesaActual = mesaRepository.findById(id).orElse(mesa);
            throw new ConflictoOperacionException("Conflicto de concurrencia: la mesa " + mesa.getNumero() + " cambió a estado " + mesaActual.getEstado() + " por otra operación");
        }

        mesa.setEstado(nuevoEstado);
        return toDto(mesa);
    }

    /**
     * Maquina de estados formal de Mesa (PRB-40):
     * - LIBRE -> ATENDIDA (mozo abre comanda), OCUPADA (pedido directo)
     * - ATENDIDA -> LIBRE (mozo cancela/libera sin pedir), OCUPADA (se confirma el pedido)
     * - OCUPADA -> CUENTA_SOLICITADA (cliente pide precuenta), LIBRE (liberacion administrativa)
     * - CUENTA_SOLICITADA -> LIBRE (cobro exitoso y liberacion), OCUPADA (cliente continua pidiendo)
     */
    public boolean esTransicionValida(EstadoMesa origen, EstadoMesa destino) {
        if (origen == null || destino == null || origen == destino) {
            return false;
        }
        return switch (origen) {
            case LIBRE -> destino == EstadoMesa.ATENDIDA || destino == EstadoMesa.OCUPADA;
            case ATENDIDA -> destino == EstadoMesa.LIBRE || destino == EstadoMesa.OCUPADA;
            case OCUPADA -> destino == EstadoMesa.CUENTA_SOLICITADA || destino == EstadoMesa.LIBRE;
            case CUENTA_SOLICITADA -> destino == EstadoMesa.LIBRE || destino == EstadoMesa.OCUPADA;
        };
    }

    private MesaDto toDto(Mesa m) {
        return new MesaDto(m.getId(), m.getNumero(), m.getCapacidad(), m.getUbicacion(), m.getEstado());
    }
}
