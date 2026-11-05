package com.lacasablanca.backend.controller;

import com.lacasablanca.backend.dto.CobrarRequest;
import com.lacasablanca.backend.dto.CobroResponse;
import com.lacasablanca.backend.dto.CrearPedidoRequest;
import com.lacasablanca.backend.dto.PedidoResponse;
import com.lacasablanca.backend.service.CobroService;
import com.lacasablanca.backend.service.PedidoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pedidos")
@CrossOrigin(origins = "*")
public class PedidoController {

    private final PedidoService pedidoService;
    private final CobroService cobroService;

    public PedidoController(PedidoService pedidoService, CobroService cobroService) {
        this.pedidoService = pedidoService;
        this.cobroService = cobroService;
    }

    @PostMapping
    public ResponseEntity<PedidoResponse> crearPedido(@Valid @RequestBody CrearPedidoRequest request,
                                                      Authentication authentication) {
        String username = (authentication != null) ? authentication.getName() : null;
        PedidoResponse response = pedidoService.registrarPedido(request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/pagos")
    @PreAuthorize("hasAnyRole('MOZO', 'CAJERO', 'ADMIN')")
    public ResponseEntity<CobroResponse> registrarPagoPedido(@PathVariable Integer id,
                                                             @Valid @RequestBody CobrarRequest request,
                                                             Authentication authentication) {
        String username = (authentication != null) ? authentication.getName() : null;
        CobroResponse response = cobroService.cobrarPedido(id, request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
