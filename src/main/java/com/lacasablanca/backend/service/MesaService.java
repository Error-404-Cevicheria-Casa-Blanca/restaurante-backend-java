package com.lacasablanca.backend.service;

import com.lacasablanca.backend.dto.MesaDto;
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
        mesa.setEstado(nuevoEstado);
        Mesa guardada = mesaRepository.save(mesa);
        return toDto(guardada);
    }

    private MesaDto toDto(Mesa m) {
        return new MesaDto(m.getId(), m.getNumero(), m.getCapacidad(), m.getUbicacion(), m.getEstado());
    }
}
