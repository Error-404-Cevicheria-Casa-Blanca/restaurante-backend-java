package com.lacasablanca.backend.controller;

import com.lacasablanca.backend.dto.EstadoMesaRequest;
import com.lacasablanca.backend.dto.MesaDto;
import com.lacasablanca.backend.service.MesaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mesas")
@CrossOrigin(origins = "*")
public class MesaController {

    private final MesaService mesaService;

    public MesaController(MesaService mesaService) {
        this.mesaService = mesaService;
    }

    @GetMapping
    public ResponseEntity<List<MesaDto>> listarMesas() {
        return ResponseEntity.ok(mesaService.listarMesasOrdenadas());
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('MOZO', 'ADMIN')")
    public ResponseEntity<MesaDto> cambiarEstado(@PathVariable Integer id,
                                                 @Valid @RequestBody EstadoMesaRequest request,
                                                 Authentication authentication) {
        String identificador = (authentication != null) ? authentication.getName() : null;
        return ResponseEntity.ok(mesaService.actualizarEstado(id, request.getEstado(), identificador));
    }
}
