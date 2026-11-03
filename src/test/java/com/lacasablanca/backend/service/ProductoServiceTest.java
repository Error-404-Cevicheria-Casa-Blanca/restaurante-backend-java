package com.lacasablanca.backend.service;

import com.lacasablanca.backend.dto.ProductoDto;
import com.lacasablanca.backend.model.Categoria;
import com.lacasablanca.backend.model.Estacion;
import com.lacasablanca.backend.model.Producto;
import com.lacasablanca.backend.repository.ProductoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    @InjectMocks
    private ProductoService productoService;

    @Test
    @DisplayName("PRB-21 E9: listarProductos sin categoriaId retorna todos los productos mapeados")
    void listarProductosSinFiltro() {
        Categoria cat = new Categoria(1, "Entradas");
        Producto p1 = new Producto("Tequeños", "Con queso", new BigDecimal("20.00"), true, cat, "http://img/1.jpg");
        p1.setId(10);
        when(productoRepository.findAll()).thenReturn(List.of(p1));

        List<ProductoDto> dtos = productoService.listarProductos(null);

        assertFalse(dtos.isEmpty());
        assertEquals(1, dtos.size());
        assertEquals(10, dtos.get(0).getId());
        assertEquals("Tequeños", dtos.get(0).getNombre());
        assertEquals(new BigDecimal("20.00"), dtos.get(0).getPrecio());
        assertEquals(1, dtos.get(0).getCategoriaId());
        assertEquals(Estacion.BARRA_FRIA, dtos.get(0).getEstacion());
        verify(productoRepository).findAll();
    }

    @Test
    @DisplayName("PRB-21 E10: listarProductos con categoriaId filtra por repositorio")
    void listarProductosConFiltro() {
        Categoria cat = new Categoria(2, "Bebidas");
        Producto p2 = new Producto("Limonada", "Jarra 1L", new BigDecimal("12.00"), true, cat, "http://img/2.jpg");
        p2.setId(20);
        when(productoRepository.findByCategoriaId(2)).thenReturn(List.of(p2));

        List<ProductoDto> dtos = productoService.listarProductos(2);

        assertFalse(dtos.isEmpty());
        assertEquals(1, dtos.size());
        assertEquals(20, dtos.get(0).getId());
        assertEquals("Limonada", dtos.get(0).getNombre());
        assertEquals(Estacion.BEBIDAS, dtos.get(0).getEstacion());
        verify(productoRepository).findByCategoriaId(2);
    }
}
