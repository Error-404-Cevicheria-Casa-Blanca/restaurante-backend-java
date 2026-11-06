package com.lacasablanca.backend.controller;

import com.lacasablanca.backend.dto.CobrarRequest;
import com.lacasablanca.backend.dto.CobroResponse;
import com.lacasablanca.backend.dto.CuentaResponse;
import com.lacasablanca.backend.dto.DividirMesaRequest;
import com.lacasablanca.backend.dto.DivisionMesaResponse;
import com.lacasablanca.backend.dto.EstadoMesaRequest;
import com.lacasablanca.backend.dto.FusionarMesasRequest;
import com.lacasablanca.backend.dto.MesaDto;
import com.lacasablanca.backend.dto.TrabajoImpresionDto;
import com.lacasablanca.backend.dto.TransferirMesaRequest;
import com.lacasablanca.backend.service.CobroService;
import com.lacasablanca.backend.service.MesaService;
import com.lacasablanca.backend.service.PedidoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/mesas")
public class MesaController {

    private final MesaService mesaService;
    private final CobroService cobroService;
    private final PedidoService pedidoService;

    public MesaController(MesaService mesaService,
                          CobroService cobroService,
                          PedidoService pedidoService) {
        this.mesaService = mesaService;
        this.cobroService = cobroService;
        this.pedidoService = pedidoService;
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

    @GetMapping("/{id}/impresion")
    @PreAuthorize("hasAnyRole('MOZO', 'CAJERO', 'ADMIN')")
    public ResponseEntity<TrabajoImpresionDto> obtenerEstadoImpresion(@PathVariable Integer id) {
        return ResponseEntity.ok(cobroService.obtenerEstadoImpresion(id));
    }

    @PostMapping("/{id}/precuenta/reimprimir")
    @PreAuthorize("hasAnyRole('MOZO', 'CAJERO', 'ADMIN')")
    public ResponseEntity<TrabajoImpresionDto> reimprimirPrecuenta(
            @PathVariable Integer id,
            @RequestParam(value = "simular_error", defaultValue = "false") boolean simularError,
            Authentication authentication) {
        String identificador = (authentication != null) ? authentication.getName() : null;
        return ResponseEntity.ok(cobroService.reimprimirPrecuenta(id, simularError, identificador));
    }

    @PostMapping("/{id}/dividir")
    @PreAuthorize("hasAnyRole('MOZO', 'CAJERO', 'ADMIN')")
    public ResponseEntity<DivisionMesaResponse> dividirMesa(@PathVariable Integer id,
                                                            @Valid @RequestBody DividirMesaRequest request,
                                                            Authentication authentication) {
        String identificador = (authentication != null) ? authentication.getName() : null;
        return ResponseEntity.ok(pedidoService.dividirMesa(id, request, identificador));
    }

    @PostMapping("/{id}/fusionar")
    @PreAuthorize("hasAnyRole('MOZO', 'CAJERO', 'ADMIN')")
    public ResponseEntity<CuentaResponse> fusionarMesas(@PathVariable Integer id,
                                                        @Valid @RequestBody FusionarMesasRequest request,
                                                        Authentication authentication) {
        String identificador = (authentication != null) ? authentication.getName() : null;
        pedidoService.fusionarMesas(id, request, identificador);
        return ResponseEntity.ok(cobroService.obtenerCuenta(id));
    }

    @PostMapping("/{id}/transferir")
    @PreAuthorize("hasAnyRole('MOZO', 'CAJERO', 'ADMIN')")
    public ResponseEntity<CuentaResponse> transferirMesa(@PathVariable Integer id,
                                                         @Valid @RequestBody TransferirMesaRequest request,
                                                         Authentication authentication) {
        String identificador = (authentication != null) ? authentication.getName() : null;
        pedidoService.transferirMesa(id, request, identificador);
        return ResponseEntity.ok(cobroService.obtenerCuenta(request.getMesaDestinoId()));
    }
}
