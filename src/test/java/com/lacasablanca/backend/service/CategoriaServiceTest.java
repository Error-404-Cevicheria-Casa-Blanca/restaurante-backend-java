package com.lacasablanca.backend.service;

import com.lacasablanca.backend.dto.CategoriaDto;
import com.lacasablanca.backend.model.Categoria;
import com.lacasablanca.backend.repository.CategoriaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceTest {

    @Mock
    private CategoriaRepository categoriaRepository;

    @InjectMocks
    private CategoriaService categoriaService;

    @Test
    @DisplayName("PRB-21 E8: listarCategorias retorna mapeo correcto de DTOs")
    void listarCategoriasExitoso() {
        Categoria c1 = new Categoria(1, "Entradas");
        Categoria c2 = new Categoria(2, "Platos de Fondo");
        when(categoriaRepository.findAll()).thenReturn(List.of(c1, c2));

        List<CategoriaDto> dtos = categoriaService.listarCategorias();

        assertFalse(dtos.isEmpty());
        assertEquals(2, dtos.size());
        assertEquals(1, dtos.get(0).getId());
        assertEquals("Entradas", dtos.get(0).getNombre());
        assertEquals(2, dtos.get(1).getId());
        assertEquals("Platos de Fondo", dtos.get(1).getNombre());
        verify(categoriaRepository).findAll();
    }
}
