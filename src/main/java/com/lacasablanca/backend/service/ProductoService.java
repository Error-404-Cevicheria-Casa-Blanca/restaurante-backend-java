package com.lacasablanca.backend.service;

import com.lacasablanca.backend.dto.ProductoDto;
import com.lacasablanca.backend.model.Producto;
import com.lacasablanca.backend.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;

    public ProductoService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @Transactional(readOnly = true)
    public List<ProductoDto> listarProductos(Integer categoriaId) {
        List<Producto> lista = (categoriaId != null) 
                ? productoRepository.findByCategoriaId(categoriaId)
                : productoRepository.findAll();
        return lista.stream()
                .map(p -> new ProductoDto(
                        p.getId(),
                        p.getNombre(),
                        p.getDescripcion(),
                        p.getPrecio(),
                        p.getDisponible(),
                        p.getCategoria().getId(),
                        p.getImagenUrl()))
                .collect(Collectors.toList());
    }
}
