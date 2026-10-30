package com.lacasablanca.backend.service;

import com.lacasablanca.backend.dto.*;
import com.lacasablanca.backend.exception.ConflictoOperacionException;
import com.lacasablanca.backend.exception.RecursoNoEncontradoException;
import com.lacasablanca.backend.model.*;
import com.lacasablanca.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final MesaRepository mesaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProductoRepository productoRepository;

    public PedidoService(PedidoRepository pedidoRepository,
                         MesaRepository mesaRepository,
                         UsuarioRepository usuarioRepository,
                         ProductoRepository productoRepository) {
        this.pedidoRepository = pedidoRepository;
        this.mesaRepository = mesaRepository;
        this.usuarioRepository = usuarioRepository;
        this.productoRepository = productoRepository;
    }

    @Transactional
    public PedidoResponse registrarPedido(CrearPedidoRequest request, String username) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("La lista de items no puede estar vacía");
        }

        Mesa mesa = mesaRepository.findById(request.getMesaId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Mesa no encontrada con ID: " + request.getMesaId()));

        if (mesa.getEstado() == EstadoMesa.OCUPADA || mesa.getEstado() == EstadoMesa.CUENTA_SOLICITADA) {
            throw new ConflictoOperacionException("La mesa " + mesa.getNumero() + " no está disponible. Estado actual: " + mesa.getEstado());
        }

        Usuario mozo = null;
        if (username != null) {
            try {
                Integer userId = Integer.parseInt(username);
                mozo = usuarioRepository.findById(userId).orElse(null);
            } catch (NumberFormatException ignored) {}
            if (mozo == null) {
                mozo = usuarioRepository.findByUsername(username).orElse(null);
            }
        }
        if (mozo == null) {
            mozo = usuarioRepository.findAll().stream().findFirst()
                    .orElseThrow(() -> new RecursoNoEncontradoException("No existe usuario registrado para asociar el pedido"));
        }

        Pedido pedido = new Pedido(mesa, mozo, EstadoPedido.PENDIENTE, BigDecimal.ZERO);
        BigDecimal total = BigDecimal.ZERO;
        List<DetallePedidoResponse> detalleResponses = new ArrayList<>();

        for (ItemPedidoRequest itemReq : request.getItems()) {
            if (itemReq.getCantidad() == null || itemReq.getCantidad() < 1) {
                throw new IllegalArgumentException("La cantidad debe ser mayor o igual a 1");
            }
            Producto producto = productoRepository.findById(itemReq.getProductoId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Producto no encontrado con ID: " + itemReq.getProductoId()));

            if (Boolean.FALSE.equals(producto.getDisponible())) {
                throw new ConflictoOperacionException("El producto '" + producto.getNombre() + "' no está disponible actualmente");
            }

            BigDecimal precioUnitario = producto.getPrecio();
            BigDecimal subtotal = precioUnitario.multiply(BigDecimal.valueOf(itemReq.getCantidad()))
                    .setScale(2, RoundingMode.HALF_UP);

            DetallePedido detalle = new DetallePedido(producto, precioUnitario, itemReq.getCantidad(), itemReq.getNotas(), subtotal);
            pedido.agregarDetalle(detalle);

            total = total.add(subtotal);
        }

        pedido.setTotal(total.setScale(2, RoundingMode.HALF_UP));

        // En la misma transacción, cambiar la mesa a OCUPADA
        mesa.setEstado(EstadoMesa.OCUPADA);
        mesaRepository.save(mesa);

        Pedido guardado = pedidoRepository.save(pedido);

        for (DetallePedido dp : guardado.getDetalles()) {
            detalleResponses.add(new DetallePedidoResponse(
                    dp.getId(),
                    dp.getProducto().getId(),
                    dp.getProducto().getNombre(),
                    dp.getPrecioUnitario(),
                    dp.getCantidad(),
                    dp.getNotas(),
                    dp.getSubtotal()
            ));
        }

        return new PedidoResponse(
                guardado.getId(),
                guardado.getMesa().getId(),
                guardado.getMozo().getId(),
                guardado.getMozo().getUsername(),
                guardado.getEstado(),
                guardado.getTotal(),
                guardado.getFechaCreacion(),
                detalleResponses
        );
    }
}
