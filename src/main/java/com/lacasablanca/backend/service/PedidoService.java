package com.lacasablanca.backend.service;

import com.lacasablanca.backend.dto.CrearPedidoRequest;
import com.lacasablanca.backend.dto.DetallePedidoResponse;
import com.lacasablanca.backend.dto.EventoPedidoDto;
import com.lacasablanca.backend.dto.ItemPedidoRequest;
import com.lacasablanca.backend.dto.PedidoResponse;
import com.lacasablanca.backend.exception.ConflictoOperacionException;
import com.lacasablanca.backend.exception.RecursoNoEncontradoException;
import com.lacasablanca.backend.model.DetallePedido;
import com.lacasablanca.backend.model.Estacion;
import com.lacasablanca.backend.model.EstadoMesa;
import com.lacasablanca.backend.model.EstadoPedido;
import com.lacasablanca.backend.model.Mesa;
import com.lacasablanca.backend.model.Pedido;
import com.lacasablanca.backend.model.Producto;
import com.lacasablanca.backend.model.Usuario;
import com.lacasablanca.backend.repository.MesaRepository;
import com.lacasablanca.backend.repository.PedidoRepository;
import com.lacasablanca.backend.repository.ProductoRepository;
import com.lacasablanca.backend.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
public class PedidoService {

    private static final BigDecimal FACTOR_IGV = new BigDecimal("1.18");

    private final Map<String, Integer> idempotenciaKeyAPedidoId = new ConcurrentHashMap<>();
    private final Map<Integer, String> pedidoIdAClientRequestId = new ConcurrentHashMap<>();
    private final Map<Integer, String> motivosAnulacion = new ConcurrentHashMap<>();
    private final Map<Integer, List<EventoPedidoDto>> eventosPorPedido = new ConcurrentHashMap<>();

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
        return registrarPedido(request, username, null);
    }

    @Transactional
    public PedidoResponse registrarPedido(CrearPedidoRequest request, String username, String headerIdempotencyKey) {
        String claveIdempotencia = resolverClaveIdempotencia(request, headerIdempotencyKey);
        if (claveIdempotencia != null) {
            Integer pedidoExistenteId = idempotenciaKeyAPedidoId.get(claveIdempotencia);
            if (pedidoExistenteId != null) {
                Pedido pedidoExistente = pedidoRepository.findById(pedidoExistenteId).orElse(null);
                if (pedidoExistente != null) {
                    PedidoResponse replayResponse = construirPedidoResponse(pedidoExistente);
                    replayResponse.setReplay(Boolean.TRUE);
                    replayResponse.setClientRequestId(claveIdempotencia);
                    return replayResponse;
                } else {
                    idempotenciaKeyAPedidoId.remove(claveIdempotencia);
                }
            }
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("La lista de items no puede estar vacía");
        }

        Mesa mesa = mesaRepository.findById(request.getMesaId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Mesa no encontrada con ID: " + request.getMesaId()
                ));

        if (mesa.getEstado() == EstadoMesa.OCUPADA || mesa.getEstado() == EstadoMesa.CUENTA_SOLICITADA) {
            throw new ConflictoOperacionException(
                    "La mesa " + mesa.getNumero() + " no está disponible. Estado actual: " + mesa.getEstado()
            );
        }

        Usuario mozo = resolverMozo(username);

        Pedido pedido = new Pedido(mesa, mozo, EstadoPedido.PENDIENTE, BigDecimal.ZERO);
        BigDecimal total = BigDecimal.ZERO;

        for (ItemPedidoRequest itemReq : request.getItems()) {
            if (itemReq.getCantidad() == null || itemReq.getCantidad() < 1) {
                throw new IllegalArgumentException("La cantidad debe ser mayor o igual a 1");
            }
            Producto producto = productoRepository.findById(itemReq.getProductoId())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "Producto no encontrado con ID: " + itemReq.getProductoId()
                    ));

            if (Boolean.FALSE.equals(producto.getDisponible())) {
                throw new ConflictoOperacionException(
                        "El producto '" + producto.getNombre() + "' no está disponible actualmente"
                );
            }

            BigDecimal precioUnitario = producto.getPrecio();
            BigDecimal subtotalItem = precioUnitario.multiply(BigDecimal.valueOf(itemReq.getCantidad()))
                    .setScale(2, RoundingMode.HALF_UP);

            DetallePedido detalle = new DetallePedido(
                    producto, precioUnitario, itemReq.getCantidad(), itemReq.getNotas(), subtotalItem
            );
            pedido.agregarDetalle(detalle);

            total = total.add(subtotalItem);
        }

        BigDecimal totalRedondeado = total.setScale(2, RoundingMode.HALF_UP);
        pedido.setTotal(totalRedondeado);

        // Transición de estado mediante máquina de estados y bloqueo optimista (PRB-40)
        mesaService.actualizarEstado(mesa.getId(), EstadoMesa.OCUPADA, username);

        Pedido guardado = pedidoRepository.save(pedido);

        if (guardado.getId() != null) {
            registrarEvento(guardado.getId(), null, EstadoPedido.PENDIENTE,
                    mozo.getUsername(), "Creación de comanda");
            if (claveIdempotencia != null) {
                idempotenciaKeyAPedidoId.put(claveIdempotencia, guardado.getId());
                pedidoIdAClientRequestId.put(guardado.getId(), claveIdempotencia);
            }
        }

        PedidoResponse respuesta = construirPedidoResponse(guardado);
        respuesta.setReplay(Boolean.FALSE);
        respuesta.setClientRequestId(claveIdempotencia);
        return respuesta;
    }

    @Transactional(readOnly = true)
    public List<PedidoResponse> listarPedidos(Integer mesaId,
                                              EstadoPedido estado,
                                              LocalDateTime desde,
                                              LocalDateTime hasta) {
        return pedidoRepository.findAll().stream()
                .filter(p -> mesaId == null || (p.getMesa() != null && mesaId.equals(p.getMesa().getId())))
                .filter(p -> estado == null || p.getEstado() == estado)
                .filter(p -> desde == null || (p.getFechaCreacion() != null && !p.getFechaCreacion().isBefore(desde)))
                .filter(p -> hasta == null || (p.getFechaCreacion() != null && !p.getFechaCreacion().isAfter(hasta)))
                .sorted(Comparator.comparing(
                        Pedido::getFechaCreacion,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .map(this::construirPedidoResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PedidoResponse obtenerPedidoPorId(Integer id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido no encontrado con ID: " + id));
        return construirPedidoResponse(pedido);
    }

    @Transactional
    public PedidoResponse actualizarEstadoPedido(Integer id,
                                                 EstadoPedido nuevoEstado,
                                                 String motivo,
                                                 String username) {
        if (nuevoEstado == null) {
            throw new IllegalArgumentException("El nuevo estado del pedido es obligatorio");
        }
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido no encontrado con ID: " + id));

        EstadoPedido estadoAnterior = pedido.getEstado();
        if (estadoAnterior == EstadoPedido.COBRADO || estadoAnterior == EstadoPedido.ANULADO) {
            throw new ConflictoOperacionException(
                    "No se puede cambiar el estado de un pedido que se encuentra " + estadoAnterior
            );
        }

        pedido.setEstado(nuevoEstado);
        Pedido guardado = pedidoRepository.save(pedido);

        String detalleMotivo = (motivo != null && !motivo.isBlank())
                ? motivo.trim()
                : "Cambio de estado a " + nuevoEstado;
        registrarEvento(guardado.getId(), estadoAnterior, nuevoEstado, username, detalleMotivo);

        return construirPedidoResponse(guardado);
    }

    @Transactional
    public PedidoResponse anularPedido(Integer id, String motivo, String username) {
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new IllegalArgumentException("El motivo de anulación es obligatorio");
        }
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido no encontrado con ID: " + id));

        EstadoPedido estadoAnterior = pedido.getEstado();
        if (estadoAnterior == EstadoPedido.COBRADO) {
            throw new ConflictoOperacionException(
                    "No se puede anular el pedido " + id + " porque ya se encuentra COBRADO"
            );
        }
        if (estadoAnterior == EstadoPedido.ANULADO) {
            return construirPedidoResponse(pedido);
        }

        pedido.setEstado(EstadoPedido.ANULADO);
        Pedido guardado = pedidoRepository.save(pedido);

        String motivoLimpio = motivo.trim();
        motivosAnulacion.put(guardado.getId(), motivoLimpio);
        registrarEvento(guardado.getId(), estadoAnterior, EstadoPedido.ANULADO, username, motivoLimpio);

        verificarYLiberarMesaSiSinPedidosActivos(guardado.getMesa());

        return construirPedidoResponse(guardado);
    }

    @Transactional
    public PedidoResponse anularItemPedido(Integer pedidoId,
                                           Integer itemId,
                                           String motivo,
                                           String username) {
        if (motivo == null || motivo.trim().isEmpty()) {
            throw new IllegalArgumentException("El motivo de anulación del ítem es obligatorio");
        }
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido no encontrado con ID: " + pedidoId));

        EstadoPedido estadoActual = pedido.getEstado();
        if (estadoActual == EstadoPedido.COBRADO || estadoActual == EstadoPedido.ANULADO) {
            throw new ConflictoOperacionException(
                    "No se pueden anular ítems de un pedido en estado " + estadoActual
            );
        }

        DetallePedido itemAEliminar = pedido.getDetalles().stream()
                .filter(dp -> dp.getId() != null && dp.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Ítem con ID " + itemId + " no encontrado en el pedido " + pedidoId
                ));

        pedido.getDetalles().remove(itemAEliminar);

        BigDecimal nuevoTotal = pedido.getDetalles().stream()
                .map(DetallePedido::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        pedido.setTotal(nuevoTotal);

        String motivoLimpio = motivo.trim();
        if (pedido.getDetalles().isEmpty()) {
            pedido.setEstado(EstadoPedido.ANULADO);
            motivosAnulacion.put(pedidoId, motivoLimpio);
            registrarEvento(pedidoId, estadoActual, EstadoPedido.ANULADO, username,
                    "Anulación total por eliminación del último ítem: " + motivoLimpio);
        } else {
            registrarEvento(pedidoId, estadoActual, estadoActual, username,
                    "Anulación de ítem " + itemId + ": " + motivoLimpio);
        }

        Pedido guardado = pedidoRepository.save(pedido);

        if (guardado.getEstado() == EstadoPedido.ANULADO) {
            verificarYLiberarMesaSiSinPedidosActivos(guardado.getMesa());
        }

        return construirPedidoResponse(guardado);
    }

    private void verificarYLiberarMesaSiSinPedidosActivos(Mesa mesa) {
        if (mesa == null || mesa.getId() == null) {
            return;
        }
        List<Pedido> activos = pedidoRepository.findByMesaIdOrderByFechaCreacionDesc(mesa.getId()).stream()
                .filter(p -> p.getEstado() != EstadoPedido.COBRADO && p.getEstado() != EstadoPedido.ANULADO)
                .collect(Collectors.toList());
        if (activos.isEmpty()) {
            mesa.setEstado(EstadoMesa.LIBRE);
            mesa.setMozo(null);
            mesa.setFechaCierre(LocalDateTime.now());
            mesaRepository.save(mesa);
        }
    }

    private String resolverClaveIdempotencia(CrearPedidoRequest request, String headerIdempotencyKey) {
        if (request != null && request.getClientRequestId() != null && !request.getClientRequestId().isBlank()) {
            return request.getClientRequestId().trim();
        }
        if (headerIdempotencyKey != null && !headerIdempotencyKey.isBlank()) {
            return headerIdempotencyKey.trim();
        }
        return null;
    }

    private Usuario resolverMozo(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new RecursoNoEncontradoException("Usuario no autenticado o sesión inválida");
        }
        Usuario mozo;
        try {
            Integer userId = Integer.parseInt(username);
            mozo = usuarioRepository.findById(userId).orElse(null);
        } catch (NumberFormatException e) {
            mozo = usuarioRepository.findByUsername(username).orElse(null);
        }
        if (mozo == null) {
            throw new RecursoNoEncontradoException("Usuario mozo no encontrado con identificador: " + username);
        }
        return mozo;
    }

    private void registrarEvento(Integer pedidoId,
                                 EstadoPedido estadoAnterior,
                                 EstadoPedido estadoNuevo,
                                 String usuario,
                                 String motivo) {
        if (pedidoId == null) {
            return;
        }
        eventosPorPedido
                .computeIfAbsent(pedidoId, k -> new CopyOnWriteArrayList<>())
                .add(new EventoPedidoDto(estadoAnterior, estadoNuevo, usuario, motivo, LocalDateTime.now()));
    }

    private PedidoResponse construirPedidoResponse(Pedido guardado) {
        BigDecimal totalRedondeado = (guardado.getTotal() != null)
                ? guardado.getTotal().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal subtotalNeto = totalRedondeado.divide(FACTOR_IGV, 2, RoundingMode.HALF_UP);
        BigDecimal igv = totalRedondeado.subtract(subtotalNeto);

        List<DetallePedidoResponse> detalleResponses = new ArrayList<>();
        if (guardado.getDetalles() != null) {
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
        }

        Map<Estacion, List<DetallePedidoResponse>> itemsPorEstacion = detalleResponses.stream()
                .collect(Collectors.groupingBy(DetallePedidoResponse::getEstacion));

        PedidoResponse response = new PedidoResponse(
                guardado.getId(),
                guardado.getMesa() != null ? guardado.getMesa().getId() : null,
                guardado.getMozo() != null ? guardado.getMozo().getId() : null,
                guardado.getMozo() != null ? guardado.getMozo().getUsername() : null,
                guardado.getEstado(),
                subtotalNeto,
                igv,
                totalRedondeado,
                guardado.getFechaCreacion(),
                detalleResponses,
                itemsPorEstacion
        );

        if (guardado.getId() != null) {
            response.setClientRequestId(pedidoIdAClientRequestId.get(guardado.getId()));
            response.setMotivoAnulacion(motivosAnulacion.get(guardado.getId()));
            List<EventoPedidoDto> eventos = eventosPorPedido.get(guardado.getId());
            response.setEventos(eventos != null ? new ArrayList<>(eventos) : List.of());
        }
        return response;
    }
}
