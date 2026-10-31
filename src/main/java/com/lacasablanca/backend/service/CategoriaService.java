package com.lacasablanca.backend.service;

import com.lacasablanca.backend.dto.CategoriaDto;
import com.lacasablanca.backend.repository.CategoriaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    public CategoriaService(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoriaDto> listarCategorias() {
        return categoriaRepository.findAll().stream()
                .map(c -> new CategoriaDto(c.getId(), c.getNombre()))
                .collect(Collectors.toList());
    }
}
