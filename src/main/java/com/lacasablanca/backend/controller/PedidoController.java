package com.lacasablanca.backend.controller;

import com.lacasablanca.backend.dto.AnularPedidoRequest;
import com.lacasablanca.backend.dto.CobrarRequest;
import com.lacasablanca.backend.dto.CobroResponse;
import com.lacasablanca.backend.dto.CrearPedidoRequest;
import com.lacasablanca.backend.dto.EstadoPedidoRequest;
import com.lacasablanca.backend.dto.PedidoResponse;
import com.lacasablanca.backend.model.EstadoPedido;
import com.lacasablanca.backend.service.CobroService;
import com.lacasablanca.backend.service.PedidoService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;
    private final CobroService cobroService;

    public PedidoController(PedidoService pedidoService, CobroService cobroService) {
        this.pedidoService = pedidoService;
        this.cobroService = cobroService;
    }

    @PostMapping
    public ResponseEntity<PedidoResponse> crearPedido(
            @Valid @RequestBody CrearPedidoRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        String username = (authentication != null) ? authentication.getName() : null;
        PedidoResponse response = pedidoService.registrarPedido(request, username, idempotencyKey);
        if (Boolean.TRUE.equals(response.getReplay())) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<PedidoResponse>> listarPedidos(
            @RequestParam(name = "mesa_id", required = false) Integer mesaId,
            @RequestParam(name = "estado", required = false) EstadoPedido estado,
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(name = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta) {
        return ResponseEntity.ok(pedidoService.listarPedidos(mesaId, estado, desde, hasta));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PedidoResponse> obtenerPedido(@PathVariable Integer id) {
        return ResponseEntity.ok(pedidoService.obtenerPedidoPorId(id));
    }

    @PostMapping("/{id}/items")
    @PreAuthorize("hasAnyRole('MOZO', 'ADMIN')")
    public ResponseEntity<PedidoResponse> ampliarPedido(
            @PathVariable Integer id,
            @Valid @RequestBody CrearPedidoRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        String username = (authentication != null) ? authentication.getName() : null;
        PedidoResponse response = pedidoService.ampliarPedidoPorId(id, request, username, idempotencyKey);
        if (Boolean.TRUE.equals(response.getReplay())) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAnyRole('COCINERO', 'CAJERO', 'MOZO', 'ADMIN')")
    public ResponseEntity<PedidoResponse> cambiarEstadoPedido(
            @PathVariable Integer id,
            @Valid @RequestBody EstadoPedidoRequest request,
            Authentication authentication) {
        String username = (authentication != null) ? authentication.getName() : null;
        return ResponseEntity.ok(
                pedidoService.actualizarEstadoPedido(id, request.getEstado(), request.getMotivo(), username)
        );
    }

    @PostMapping("/{id}/anular")
    @PreAuthorize("hasAnyRole('MOZO', 'CAJERO', 'ADMIN')")
    public ResponseEntity<PedidoResponse> anularPedido(
            @PathVariable Integer id,
            @Valid @RequestBody AnularPedidoRequest request,
            Authentication authentication) {
        String username = (authentication != null) ? authentication.getName() : null;
        return ResponseEntity.ok(pedidoService.anularPedido(id, request.getMotivo(), username));
    }

    @PostMapping("/{id}/items/{itemId}/anular")
    @PreAuthorize("hasAnyRole('MOZO', 'CAJERO', 'ADMIN')")
    public ResponseEntity<PedidoResponse> anularItemPedido(
            @PathVariable Integer id,
            @PathVariable Integer itemId,
            @Valid @RequestBody AnularPedidoRequest request,
            Authentication authentication) {
        String username = (authentication != null) ? authentication.getName() : null;
        return ResponseEntity.ok(pedidoService.anularItemPedido(id, itemId, request.getMotivo(), username));
    }

    @PostMapping({"/{id}/pagos", "/{id}/cobrar"})
    @PreAuthorize("hasAnyRole('MOZO', 'CAJERO', 'ADMIN')")
    public ResponseEntity<CobroResponse> registrarPagoPedido(
            @PathVariable Integer id,
            @Valid @RequestBody CobrarRequest request,
            Authentication authentication) {
        String username = (authentication != null) ? authentication.getName() : null;
        CobroResponse response = cobroService.cobrarPedido(id, request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
