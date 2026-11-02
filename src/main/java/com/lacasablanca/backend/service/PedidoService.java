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
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PedidoService {

    private static final BigDecimal FACTOR_IGV = new BigDecimal("1.18");

    private final PedidoRepository pedidoRepository;
    private final MesaRepository mesaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProductoRepository productoRepository;
    private final MesaService mesaService;

    public PedidoService(PedidoRepository pedidoRepository,
                         MesaRepository mesaRepository,
                         UsuarioRepository usuarioRepository,
                         ProductoRepository productoRepository,
                         MesaService mesaService) {
        this.pedidoRepository = pedidoRepository;
        this.mesaRepository = mesaRepository;
        this.usuarioRepository = usuarioRepository;
        this.productoRepository = productoRepository;
        this.mesaService = mesaService;
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

        if (username == null || username.trim().isEmpty()) {
            throw new RecursoNoEncontradoException("Usuario no autenticado o sesión inválida");
        }

        Usuario mozo = null;
        try {
            Integer userId = Integer.parseInt(username);
            mozo = usuarioRepository.findById(userId).orElse(null);
        } catch (NumberFormatException e) {
            mozo = usuarioRepository.findByUsername(username).orElse(null);
        }

        if (mozo == null) {
            throw new RecursoNoEncontradoException("Usuario mozo no encontrado con identificador: " + username);
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
            BigDecimal subtotalItem = precioUnitario.multiply(BigDecimal.valueOf(itemReq.getCantidad()))
                    .setScale(2, RoundingMode.HALF_UP);

            DetallePedido detalle = new DetallePedido(producto, precioUnitario, itemReq.getCantidad(), itemReq.getNotas(), subtotalItem);
            pedido.agregarDetalle(detalle);

            total = total.add(subtotalItem);
        }

        BigDecimal totalRedondeado = total.setScale(2, RoundingMode.HALF_UP);
        pedido.setTotal(totalRedondeado);

        // Desglose informativo D13: Precios incluyen IGV (18%)
        // subtotal = total / 1.18, igv = total - subtotal
        BigDecimal subtotalNeto = totalRedondeado.divide(FACTOR_IGV, 2, RoundingMode.HALF_UP);
        BigDecimal igv = totalRedondeado.subtract(subtotalNeto);

        // Transición de estado mediante máquina de estados y bloqueo optimista (PRB-40)
        mesaService.actualizarEstado(mesa.getId(), EstadoMesa.OCUPADA, username);

        Pedido guardado = pedidoRepository.save(pedido);

        for (DetallePedido dp : guardado.getDetalles()) {
            detalleResponses.add(new DetallePedidoResponse(
                    dp.getId(),
                    dp.getProducto().getId(),
                    dp.getProducto().getNombre(),
                    dp.getProducto().getEstacion(),
                    dp.getPrecioUnitario(),
                    dp.getCantidad(),
                    dp.getNotas(),
                    dp.getSubtotal()
            ));
        }

        Map<Estacion, List<DetallePedidoResponse>> itemsPorEstacion = detalleResponses.stream()
                .collect(Collectors.groupingBy(DetallePedidoResponse::getEstacion));

        return new PedidoResponse(
                guardado.getId(),
                guardado.getMesa().getId(),
                guardado.getMozo().getId(),
                guardado.getMozo().getUsername(),
                guardado.getEstado(),
                subtotalNeto,
                igv,
                guardado.getTotal(),
                guardado.getFechaCreacion(),
                detalleResponses,
                itemsPorEstacion
        );
    }
}
