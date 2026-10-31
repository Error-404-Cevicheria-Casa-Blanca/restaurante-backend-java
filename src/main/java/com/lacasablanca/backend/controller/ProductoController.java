package com.lacasablanca.backend.controller;

import com.lacasablanca.backend.dto.ProductoDto;
import com.lacasablanca.backend.service.ProductoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/productos")
@CrossOrigin(origins = "*")
public class ProductoController {

    private final ProductoService productoService;

    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    @GetMapping
    public ResponseEntity<List<ProductoDto>> listarProductos(
            @RequestParam(required = false, name = "categoria_id") Integer categoriaId) {
        return ResponseEntity.ok(productoService.listarProductos(categoriaId));
    }
}
