package com.lacasablanca.backend.controller;

import com.lacasablanca.backend.dto.CobrarRequest;
import com.lacasablanca.backend.dto.CobroResponse;
import com.lacasablanca.backend.dto.CuentaResponse;
import com.lacasablanca.backend.dto.EstadoMesaRequest;
import com.lacasablanca.backend.dto.MesaDto;
import com.lacasablanca.backend.service.CobroService;
import com.lacasablanca.backend.service.MesaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mesas")
@CrossOrigin(origins = "*")
public class MesaController {

    private final MesaService mesaService;
    private final CobroService cobroService;

    public MesaController(MesaService mesaService, CobroService cobroService) {
        this.mesaService = mesaService;
        this.cobroService = cobroService;
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

    @GetMapping("/{id}/cuenta")
    @PreAuthorize("hasAnyRole('MOZO', 'CAJERO', 'ADMIN')")
    public ResponseEntity<CuentaResponse> obtenerCuenta(@PathVariable Integer id) {
        return ResponseEntity.ok(cobroService.obtenerCuenta(id));
    }

    @PostMapping("/{id}/cobrar")
    @PreAuthorize("hasAnyRole('MOZO', 'CAJERO', 'ADMIN')")
    public ResponseEntity<CobroResponse> cobrar(@PathVariable Integer id,
                                                @Valid @RequestBody CobrarRequest request,
                                                Authentication authentication) {
        String identificador = (authentication != null) ? authentication.getName() : null;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cobroService.cobrar(id, request, identificador));
    }

    @PostMapping("/{id}/precuenta")
    @PreAuthorize("hasAnyRole('MOZO', 'CAJERO', 'ADMIN')")
    public ResponseEntity<CuentaResponse> solicitarPrecuenta(@PathVariable Integer id,
                                                             Authentication authentication) {
        String identificador = (authentication != null) ? authentication.getName() : null;
        return ResponseEntity.ok(cobroService.solicitarPrecuenta(id, identificador));
    }
}
