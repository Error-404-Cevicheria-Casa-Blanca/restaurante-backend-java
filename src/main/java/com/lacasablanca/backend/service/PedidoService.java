package com.lacasablanca.backend.service;

import com.lacasablanca.backend.dto.CrearPedidoRequest;
import com.lacasablanca.backend.dto.DetallePedidoResponse;
import com.lacasablanca.backend.dto.DividirMesaRequest;
import com.lacasablanca.backend.dto.DivisionMesaResponse;
import com.lacasablanca.backend.dto.EventoPedidoDto;
import com.lacasablanca.backend.dto.FusionarMesasRequest;
import com.lacasablanca.backend.dto.ItemPedidoRequest;
import com.lacasablanca.backend.dto.ModificadorItemDto;
import com.lacasablanca.backend.dto.PedidoResponse;
import com.lacasablanca.backend.dto.TransferirMesaRequest;
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
    private final Map<Integer, Integer> pedidoActivoPorMesaId = new ConcurrentHashMap<>();
    private final Map<Integer, String> pedidoIdAClientRequestId = new ConcurrentHashMap<>();
    private final Map<Integer, String> motivosAnulacion = new ConcurrentHashMap<>();
    private final Map<Integer, List<EventoPedidoDto>> eventosPorPedido = new ConcurrentHashMap<>();
    private final Map<Integer, List<ModificadorItemDto>> modificadoresPorDetalleId = new ConcurrentHashMap<>();
    private final Map<Integer, String> tipoPorPedidoId = new ConcurrentHashMap<>();
    private final Map<Integer, String> clientePorPedidoId = new ConcurrentHashMap<>();

    private final PedidoRepository pedidoRepository;
    private final MesaRepository mesaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProductoRepository productoRepository;
    private final com.lacasablanca.backend.repository.EventoPedidoRepository eventoPedidoRepository;
    private final MesaService mesaService;

    public PedidoService(PedidoRepository pedidoRepository,
                         MesaRepository mesaRepository,
                         UsuarioRepository usuarioRepository,
                         ProductoRepository productoRepository,
                         com.lacasablanca.backend.repository.EventoPedidoRepository eventoPedidoRepository,
                         MesaService mesaService) {
        this.pedidoRepository = pedidoRepository;
        this.mesaRepository = mesaRepository;
        this.usuarioRepository = usuarioRepository;
        this.productoRepository = productoRepository;
        this.eventoPedidoRepository = eventoPedidoRepository;
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
            Pedido pedidoExistente = pedidoRepository.findByClientRequestId(claveIdempotencia)
                    .orElseGet(() -> {
                        Integer idMem = idempotenciaKeyAPedidoId.get(claveIdempotencia);
                        return idMem != null ? pedidoRepository.findById(idMem).orElse(null) : null;
                    });
            if (pedidoExistente != null) {
                PedidoResponse replayResponse = construirPedidoResponse(pedidoExistente);
                replayResponse.setReplay(Boolean.TRUE);
                replayResponse.setClientRequestId(claveIdempotencia);
                return replayResponse;
            }
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("La lista de items no puede estar vacía");
        }

        Mesa mesa = null;
        if (request.getMesaId() != null) {
            mesa = mesaRepository.findById(request.getMesaId())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "Mesa no encontrada con ID: " + request.getMesaId()
                    ));

            if (mesa.getEstado() == EstadoMesa.CUENTA_SOLICITADA) {
                throw new ConflictoOperacionException(
                        "La mesa " + mesa.getNumero() + " no está disponible. Estado actual: " + mesa.getEstado()
                );
            }
            if (mesa.getEstado() == EstadoMesa.OCUPADA) {
                Pedido pedidoActivo = null;
                Integer idActivo = pedidoActivoPorMesaId.get(mesa.getId());
                if (idActivo != null) {
                    pedidoActivo = pedidoRepository.findById(idActivo).orElse(null);
                    if (pedidoActivo != null && (pedidoActivo.getEstado() == EstadoPedido.COBRADO || pedidoActivo.getEstado() == EstadoPedido.ANULADO)) {
                        pedidoActivoPorMesaId.remove(mesa.getId());
                        pedidoActivo = null;
                    }
                }
                if (pedidoActivo == null) {
                    List<Pedido> activosDb = pedidoRepository.findByMesaIdAndEstadoNotOrderByFechaCreacionAsc(
                            mesa.getId(), EstadoPedido.COBRADO
                    ).stream().filter(p -> p.getEstado() != EstadoPedido.ANULADO).collect(Collectors.toList());
                    if (!activosDb.isEmpty()) {
                        pedidoActivo = activosDb.get(activosDb.size() - 1);
                        pedidoActivoPorMesaId.put(mesa.getId(), pedidoActivo.getId());
                    }
                }
                if (pedidoActivo != null
                        && pedidoActivo.getEstado() != EstadoPedido.COBRADO
                        && pedidoActivo.getEstado() != EstadoPedido.ANULADO
                        && pedidoActivo.getMesa() != null
                        && mesa.getId().equals(pedidoActivo.getMesa().getId())) {
                    return ampliarPedidoExistente(
                            pedidoActivo, request.getItems(), username, claveIdempotencia
                    );
                }
                throw new ConflictoOperacionException(
                        "La mesa " + mesa.getNumero() + " no está disponible. Estado actual: " + mesa.getEstado()
                );
            }
        }

        Usuario mozo = resolverMozo(username);

        Pedido pedido = new Pedido(mesa, mozo, EstadoPedido.PENDIENTE, BigDecimal.ZERO);
        if (claveIdempotencia != null) {
            pedido.setClientRequestId(claveIdempotencia);
        }
        String tipoResuelto = (request.getTipoPedido() != null && !request.getTipoPedido().isBlank())
                ? request.getTipoPedido().trim()
                : (mesa == null ? "PARA_LLEVAR" : "SALON");
        pedido.setTipoPedido(tipoResuelto);
        if (request.getClienteNombre() != null && !request.getClienteNombre().isBlank()) {
            pedido.setClienteNombre(request.getClienteNombre().trim());
        }

        BigDecimal total = BigDecimal.ZERO;
        List<List<ModificadorItemDto>> modsPorIndice = new ArrayList<>();

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

            List<ModificadorItemDto> modsItem = limpiarModificadores(itemReq.getModificadores());
            modsPorIndice.add(modsItem);

            BigDecimal recargoMods = calcularRecargoModificadores(modsItem);
            BigDecimal precioUnitario = producto.getPrecio().add(recargoMods).setScale(2, RoundingMode.HALF_UP);
            BigDecimal subtotalItem = precioUnitario.multiply(BigDecimal.valueOf(itemReq.getCantidad()))
                    .setScale(2, RoundingMode.HALF_UP);

            String notasFinales = componerNotasConModificadores(itemReq.getNotas(), modsItem);

            DetallePedido detalle = new DetallePedido(
                    producto, precioUnitario, itemReq.getCantidad(), notasFinales, subtotalItem
            );
            if (!modsItem.isEmpty()) {
                detalle.setModificadoresJson(serializarModificadoresAJson(modsItem));
            }
            pedido.agregarDetalle(detalle);

            total = total.add(subtotalItem);
        }

        BigDecimal totalRedondeado = total.setScale(2, RoundingMode.HALF_UP);
        pedido.setTotal(totalRedondeado);

        // Transición de estado mediante máquina de estados y bloqueo optimista (PRB-40) solo si tiene mesa
        if (mesa != null) {
            mesaService.actualizarEstado(mesa.getId(), EstadoMesa.OCUPADA, username);
        }

        Pedido guardado = pedidoRepository.save(pedido);

        if (guardado.getId() != null) {
            if (mesa != null && mesa.getId() != null) {
                pedidoActivoPorMesaId.put(mesa.getId(), guardado.getId());
            }
            tipoPorPedidoId.put(guardado.getId(), tipoResuelto);
            if (request.getClienteNombre() != null && !request.getClienteNombre().isBlank()) {
                clientePorPedidoId.put(guardado.getId(), request.getClienteNombre().trim());
            }
        }

        if (guardado.getDetalles() != null) {
            for (int i = 0; i < guardado.getDetalles().size() && i < modsPorIndice.size(); i++) {
                DetallePedido dp = guardado.getDetalles().get(i);
                List<ModificadorItemDto> mods = modsPorIndice.get(i);
                if (dp.getId() != null && !mods.isEmpty()) {
                    modificadoresPorDetalleId.put(dp.getId(), mods);
                }
            }
        }

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

    @Transactional
    public PedidoResponse ampliarPedidoPorId(Integer pedidoId,
                                             CrearPedidoRequest request,
                                             String username,
                                             String headerIdempotencyKey) {
        String claveIdempotencia = resolverClaveIdempotencia(request, headerIdempotencyKey);
        if (claveIdempotencia != null) {
            Pedido existente = pedidoRepository.findByClientRequestId(claveIdempotencia)
                    .orElseGet(() -> {
                        Integer idMem = idempotenciaKeyAPedidoId.get(claveIdempotencia);
                        return idMem != null ? pedidoRepository.findById(idMem).orElse(null) : null;
                    });
            if (existente != null) {
                PedidoResponse replay = construirPedidoResponse(existente);
                replay.setReplay(Boolean.TRUE);
                replay.setClientRequestId(claveIdempotencia);
                return replay;
            }
        }
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("La lista de items no puede estar vacía");
        }
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido no encontrado con ID: " + pedidoId));
        if (pedido.getEstado() == EstadoPedido.COBRADO || pedido.getEstado() == EstadoPedido.ANULADO) {
            throw new ConflictoOperacionException(
                    "No se puede ampliar un pedido en estado " + pedido.getEstado()
            );
        }
        if (pedido.getMesa() != null && pedido.getMesa().getEstado() == EstadoMesa.CUENTA_SOLICITADA) {
            throw new ConflictoOperacionException(
                    "La mesa " + pedido.getMesa().getNumero()
                            + " no está disponible. Estado actual: CUENTA_SOLICITADA"
            );
        }
        return ampliarPedidoExistente(pedido, request.getItems(), username, claveIdempotencia);
    }

    private PedidoResponse ampliarPedidoExistente(Pedido pedido,
                                                  List<ItemPedidoRequest> nuevosItems,
                                                  String username,
                                                  String claveIdempotencia) {
        Usuario mozo = resolverMozo(username);
        BigDecimal totalActual = pedido.getTotal() != null ? pedido.getTotal() : BigDecimal.ZERO;
        int indiceInicioNuevos = pedido.getDetalles() != null ? pedido.getDetalles().size() : 0;
        List<List<ModificadorItemDto>> modsNuevos = new ArrayList<>();

        for (ItemPedidoRequest itemReq : nuevosItems) {
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
            List<ModificadorItemDto> modsItem = limpiarModificadores(itemReq.getModificadores());
            modsNuevos.add(modsItem);
            BigDecimal recargoMods = calcularRecargoModificadores(modsItem);
            BigDecimal precioUnitario = producto.getPrecio().add(recargoMods).setScale(2, RoundingMode.HALF_UP);
            BigDecimal subtotalItem = precioUnitario.multiply(BigDecimal.valueOf(itemReq.getCantidad()))
                    .setScale(2, RoundingMode.HALF_UP);
            String notasFinales = componerNotasConModificadores(itemReq.getNotas(), modsItem);

            DetallePedido detalle = new DetallePedido(
                    producto, precioUnitario, itemReq.getCantidad(), notasFinales, subtotalItem
            );
            if (!modsItem.isEmpty()) {
                detalle.setModificadoresJson(serializarModificadoresAJson(modsItem));
            }
            pedido.agregarDetalle(detalle);
            totalActual = totalActual.add(subtotalItem);
        }

        pedido.setTotal(totalActual.setScale(2, RoundingMode.HALF_UP));
        Pedido guardado = pedidoRepository.save(pedido);

        if (guardado.getDetalles() != null) {
            for (int i = 0; i < modsNuevos.size(); i++) {
                int idx = indiceInicioNuevos + i;
                if (idx < guardado.getDetalles().size()) {
                    DetallePedido dp = guardado.getDetalles().get(idx);
                    List<ModificadorItemDto> mods = modsNuevos.get(i);
                    if (dp.getId() != null && !mods.isEmpty()) {
                        modificadoresPorDetalleId.put(dp.getId(), mods);
                    }
                }
            }
        }

        if (guardado.getId() != null) {
            registrarEvento(guardado.getId(), guardado.getEstado(), guardado.getEstado(),
                    mozo.getUsername(), "Ampliación de comanda existente (+" + nuevosItems.size() + " ítems)");
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
        String motivoLimpio = motivo.trim();
        pedido.setMotivoAnulacion(motivoLimpio);
        Pedido guardado = pedidoRepository.save(pedido);

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
            pedido.setMotivoAnulacion(motivoLimpio);
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

    @Transactional
    public DivisionMesaResponse dividirMesa(Integer mesaOrigenId, DividirMesaRequest request, String username) {
        if (request == null || request.getItemIds() == null || request.getItemIds().isEmpty()) {
            throw new IllegalArgumentException("Debe indicar al menos un item_id para dividir la cuenta");
        }

        Mesa mesaOrigen = mesaRepository.findById(mesaOrigenId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mesa no encontrada con ID: " + mesaOrigenId));

        if (mesaOrigen.getEstado() == EstadoMesa.LIBRE) {
            throw new ConflictoOperacionException(
                    "No se puede dividir la mesa " + mesaOrigen.getNumero() + " porque se encuentra LIBRE"
            );
        }

        List<Pedido> pedidosActivos = pedidoRepository.findByMesaIdOrderByFechaCreacionDesc(mesaOrigenId).stream()
                .filter(p -> p.getEstado() != EstadoPedido.COBRADO && p.getEstado() != EstadoPedido.ANULADO)
                .collect(Collectors.toList());

        if (pedidosActivos.isEmpty()) {
            throw new ConflictoOperacionException(
                    "La mesa " + mesaOrigen.getNumero() + " no tiene pedidos activos para dividir"
            );
        }

        Mesa mesaDestino = mesaOrigen;
        if (request.getMesaDestinoId() != null && !request.getMesaDestinoId().equals(mesaOrigenId)) {
            mesaDestino = mesaRepository.findById(request.getMesaDestinoId())
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "Mesa destino no encontrada con ID: " + request.getMesaDestinoId()
                    ));
            if (mesaDestino.getEstado() == EstadoMesa.OCUPADA
                    || mesaDestino.getEstado() == EstadoMesa.CUENTA_SOLICITADA) {
                throw new ConflictoOperacionException(
                        "La mesa destino " + mesaDestino.getNumero()
                                + " ya se encuentra ocupada. Estado actual: " + mesaDestino.getEstado()
                );
            }
        }

        Pedido pedidoOrigen = pedidosActivos.get(0);
        List<DetallePedido> itemsAMover = new ArrayList<>();
        for (Integer itemId : request.getItemIds()) {
            DetallePedido encontrado = pedidoOrigen.getDetalles().stream()
                    .filter(dp -> dp.getId() != null && dp.getId().equals(itemId))
                    .findFirst()
                    .orElseThrow(() -> new RecursoNoEncontradoException(
                            "Ítem con ID " + itemId + " no pertenece al pedido activo de la mesa " + mesaOrigenId
                    ));
            itemsAMover.add(encontrado);
        }

        if (itemsAMover.size() >= pedidoOrigen.getDetalles().size()) {
            throw new ConflictoOperacionException(
                    "No se pueden separar todos los ítems en una división; debe quedar al menos un ítem en la cuenta original"
            );
        }

        Pedido nuevoPedido = new Pedido(
                mesaDestino,
                pedidoOrigen.getMozo(),
                pedidoOrigen.getEstado(),
                BigDecimal.ZERO
        );

        for (DetallePedido itemOriginal : itemsAMover) {
            List<ModificadorItemDto> modsPrevios = obtenerModificadoresDeDetalle(itemOriginal);
            pedidoOrigen.getDetalles().remove(itemOriginal);

            DetallePedido clon = new DetallePedido(
                    itemOriginal.getProducto(),
                    itemOriginal.getPrecioUnitario(),
                    itemOriginal.getCantidad(),
                    itemOriginal.getNotas(),
                    itemOriginal.getSubtotal()
            );
            if (itemOriginal.getModificadoresJson() != null && !itemOriginal.getModificadoresJson().isBlank()) {
                clon.setModificadoresJson(itemOriginal.getModificadoresJson());
            } else if (!modsPrevios.isEmpty()) {
                clon.setModificadoresJson(serializarModificadoresAJson(modsPrevios));
            }
            nuevoPedido.agregarDetalle(clon);
            if (itemOriginal.getId() != null) {
                modificadoresPorDetalleId.remove(itemOriginal.getId());
            }
            if (!modsPrevios.isEmpty() && clon.getId() != null) {
                modificadoresPorDetalleId.put(clon.getId(), modsPrevios);
            }
        }

        BigDecimal nuevoTotalOrigen = pedidoOrigen.getDetalles().stream()
                .map(DetallePedido::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        pedidoOrigen.setTotal(nuevoTotalOrigen);

        BigDecimal nuevoTotalDestino = nuevoPedido.getDetalles().stream()
                .map(DetallePedido::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        nuevoPedido.setTotal(nuevoTotalDestino);

        if (!mesaDestino.getId().equals(mesaOrigen.getId())) {
            mesaDestino.setEstado(EstadoMesa.OCUPADA);
            mesaDestino.setMozo(pedidoOrigen.getMozo());
            mesaDestino.setFechaApertura(LocalDateTime.now());
            mesaDestino.setFechaCierre(null);
            mesaRepository.save(mesaDestino);
        }

        Pedido origenGuardado = pedidoRepository.save(pedidoOrigen);
        Pedido nuevoGuardado = pedidoRepository.save(nuevoPedido);

        registrarEvento(origenGuardado.getId(), origenGuardado.getEstado(), origenGuardado.getEstado(),
                username, "División de cuenta hacia pedido #" + nuevoGuardado.getId());
        registrarEvento(nuevoGuardado.getId(), null, nuevoGuardado.getEstado(),
                username, "Cuenta generada por división desde pedido #" + origenGuardado.getId());

        BigDecimal totalCombinado = nuevoTotalOrigen.add(nuevoTotalDestino).setScale(2, RoundingMode.HALF_UP);

        return new DivisionMesaResponse(
                mesaOrigen.getId(),
                mesaDestino.getId(),
                construirPedidoResponse(origenGuardado),
                construirPedidoResponse(nuevoGuardado),
                totalCombinado
        );
    }

    @Transactional
    public void fusionarMesas(Integer mesaDestinoId, FusionarMesasRequest request, String username) {
        if (request == null || request.getMesaOrigenId() == null) {
            throw new IllegalArgumentException("El mesa_origen_id es obligatorio para fusionar");
        }
        Integer mesaOrigenId = request.getMesaOrigenId();
        if (mesaDestinoId.equals(mesaOrigenId)) {
            throw new ConflictoOperacionException("No se puede fusionar una mesa consigo misma");
        }

        Mesa mesaDestino = mesaRepository.findById(mesaDestinoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mesa destino no encontrada con ID: " + mesaDestinoId));
        Mesa mesaOrigen = mesaRepository.findById(mesaOrigenId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mesa origen no encontrada con ID: " + mesaOrigenId));

        if (mesaDestino.getEstado() == EstadoMesa.LIBRE || mesaOrigen.getEstado() == EstadoMesa.LIBRE) {
            throw new ConflictoOperacionException("Ambas mesas deben estar ocupadas para poder fusionarse");
        }
        if (mesaDestino.getEstado() == EstadoMesa.CUENTA_SOLICITADA || mesaOrigen.getEstado() == EstadoMesa.CUENTA_SOLICITADA) {
            throw new ConflictoOperacionException("No se pueden fusionar mesas en estado CUENTA_SOLICITADA");
        }

        List<Pedido> pedidosDestino = pedidoRepository.findByMesaIdOrderByFechaCreacionDesc(mesaDestinoId).stream()
                .filter(p -> p.getEstado() != EstadoPedido.COBRADO && p.getEstado() != EstadoPedido.ANULADO)
                .collect(Collectors.toList());
        List<Pedido> pedidosOrigen = pedidoRepository.findByMesaIdOrderByFechaCreacionDesc(mesaOrigenId).stream()
                .filter(p -> p.getEstado() != EstadoPedido.COBRADO && p.getEstado() != EstadoPedido.ANULADO)
                .collect(Collectors.toList());

        if (pedidosDestino.isEmpty() || pedidosOrigen.isEmpty()) {
            throw new ConflictoOperacionException("Ambas mesas deben tener pedidos activos para fusionarse");
        }

        for (Pedido p : pedidosOrigen) {
            p.setMesa(mesaDestino);
            pedidoRepository.save(p);
            registrarEvento(p.getId(), p.getEstado(), p.getEstado(), username,
                    "Fusión de mesa " + mesaOrigen.getNumero() + " hacia mesa " + mesaDestino.getNumero());
        }

        mesaOrigen.setEstado(EstadoMesa.LIBRE);
        mesaOrigen.setMozo(null);
        mesaOrigen.setFechaCierre(LocalDateTime.now());
        mesaRepository.save(mesaOrigen);
    }

    @Transactional
    public void transferirMesa(Integer mesaOrigenId, TransferirMesaRequest request, String username) {
        if (request == null || request.getMesaDestinoId() == null) {
            throw new IllegalArgumentException("El mesa_destino_id es obligatorio para transferir");
        }
        Integer mesaDestinoId = request.getMesaDestinoId();
        if (mesaOrigenId.equals(mesaDestinoId)) {
            throw new ConflictoOperacionException("La mesa destino debe ser distinta a la mesa de origen");
        }

        Mesa mesaOrigen = mesaRepository.findById(mesaOrigenId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mesa origen no encontrada con ID: " + mesaOrigenId));
        Mesa mesaDestino = mesaRepository.findById(mesaDestinoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mesa destino no encontrada con ID: " + mesaDestinoId));

        if (mesaOrigen.getEstado() == EstadoMesa.LIBRE) {
            throw new ConflictoOperacionException(
                    "La mesa de origen " + mesaOrigen.getNumero() + " está LIBRE y no tiene pedidos para transferir"
            );
        }

        if (mesaDestino.getEstado() == EstadoMesa.OCUPADA
                || mesaDestino.getEstado() == EstadoMesa.CUENTA_SOLICITADA) {
            throw new ConflictoOperacionException(
                    "La mesa destino " + mesaDestino.getNumero()
                            + " no está disponible para transferencia. Estado actual: " + mesaDestino.getEstado()
            );
        }

        List<Pedido> pedidosOrigen = pedidoRepository.findByMesaIdOrderByFechaCreacionDesc(mesaOrigenId).stream()
                .filter(p -> p.getEstado() != EstadoPedido.COBRADO && p.getEstado() != EstadoPedido.ANULADO)
                .collect(Collectors.toList());

        if (pedidosOrigen.isEmpty()) {
            throw new ConflictoOperacionException(
                    "La mesa " + mesaOrigen.getNumero() + " no tiene pedidos activos para transferir"
            );
        }

        for (Pedido p : pedidosOrigen) {
            p.setMesa(mesaDestino);
            pedidoRepository.save(p);
            registrarEvento(p.getId(), p.getEstado(), p.getEstado(), username,
                    "Transferencia de mesa " + mesaOrigen.getNumero() + " a mesa " + mesaDestino.getNumero());
        }

        mesaDestino.setEstado(mesaOrigen.getEstado());
        mesaDestino.setMozo(mesaOrigen.getMozo() != null ? mesaOrigen.getMozo() : pedidosOrigen.get(0).getMozo());
        mesaDestino.setFechaApertura(
                mesaOrigen.getFechaApertura() != null ? mesaOrigen.getFechaApertura() : LocalDateTime.now()
        );
        mesaDestino.setFechaCierre(null);
        mesaRepository.save(mesaDestino);

        mesaOrigen.setEstado(EstadoMesa.LIBRE);
        mesaOrigen.setMozo(null);
        mesaOrigen.setFechaCierre(LocalDateTime.now());
        mesaRepository.save(mesaOrigen);
    }

    public List<ModificadorItemDto> obtenerModificadoresDeDetalle(DetallePedido dp) {
        if (dp == null) {
            return List.of();
        }
        if (dp.getModificadoresJson() != null && !dp.getModificadoresJson().isBlank()) {
            List<ModificadorItemDto> deserializados = deserializarModificadoresDesdeJson(dp.getModificadoresJson());
            if (!deserializados.isEmpty()) {
                return deserializados;
            }
        }
        if (dp.getId() != null && modificadoresPorDetalleId.containsKey(dp.getId())) {
            return new ArrayList<>(modificadoresPorDetalleId.get(dp.getId()));
        }
        return extraerModificadoresDesdeNotas(dp.getNotas());
    }

    private List<ModificadorItemDto> limpiarModificadores(List<ModificadorItemDto> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<ModificadorItemDto> resultado = new ArrayList<>();
        for (ModificadorItemDto mod : raw) {
            if (mod != null && mod.getNombre() != null && !mod.getNombre().isBlank()) {
                BigDecimal precio = mod.getPrecioAdicional() != null ? mod.getPrecioAdicional() : BigDecimal.ZERO;
                if (precio.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("El precio adicional del modificador no puede ser negativo");
                }
                resultado.add(new ModificadorItemDto(mod.getNombre().trim(), precio));
            }
        }
        return resultado;
    }

    private BigDecimal calcularRecargoModificadores(List<ModificadorItemDto> mods) {
        BigDecimal suma = BigDecimal.ZERO;
        for (ModificadorItemDto mod : mods) {
            if (mod.getPrecioAdicional() != null && mod.getPrecioAdicional().compareTo(BigDecimal.ZERO) > 0) {
                suma = suma.add(mod.getPrecioAdicional());
            }
        }
        return suma;
    }

    private String componerNotasConModificadores(String notasOriginales, List<ModificadorItemDto> mods) {
        if (mods == null || mods.isEmpty()) {
            return notasOriginales;
        }
        String etiquetaMods = "[Modificadores: "
                + mods.stream().map(ModificadorItemDto::getNombre).collect(Collectors.joining(", "))
                + "]";
        String combinada;
        if (notasOriginales == null || notasOriginales.isBlank()) {
            combinada = etiquetaMods;
        } else {
            combinada = notasOriginales.trim() + " " + etiquetaMods;
        }
        if (combinada.length() > 255) {
            return notasOriginales;
        }
        return combinada;
    }

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private String serializarModificadoresAJson(List<ModificadorItemDto> mods) {
        if (mods == null || mods.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(mods);
        } catch (Exception e) {
            return null;
        }
    }

    private List<ModificadorItemDto> deserializarModificadoresDesdeJson(String json) {
        if (json == null || json.isBlank() || !json.startsWith("[")) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ModificadorItemDto.class)
            );
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<ModificadorItemDto> extraerModificadoresDesdeNotas(String notas) {
        if (notas == null || !notas.contains("[Modificadores:")) {
            return List.of();
        }
        int inicio = notas.indexOf("[Modificadores:") + "[Modificadores:".length();
        int fin = notas.indexOf("]", inicio);
        if (fin <= inicio) {
            return List.of();
        }
        String contenido = notas.substring(inicio, fin).trim();
        if (contenido.isEmpty()) {
            return List.of();
        }
        String[] partes = contenido.split(",");
        List<ModificadorItemDto> mods = new ArrayList<>();
        for (String p : partes) {
            if (!p.isBlank()) {
                mods.add(new ModificadorItemDto(p.trim(), BigDecimal.ZERO));
            }
        }
        return mods;
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

        try {
            if (eventoPedidoRepository != null) {
                Pedido p = pedidoRepository.findById(pedidoId).orElse(null);
                if (p != null) {
                    Usuario u = null;
                    if (usuario != null && !usuario.isBlank()) {
                        try {
                            Integer uid = Integer.parseInt(usuario);
                            u = usuarioRepository.findById(uid).orElse(null);
                        } catch (NumberFormatException e) {
                            u = usuarioRepository.findByUsername(usuario).orElse(null);
                        }
                    }
                    eventoPedidoRepository.save(new com.lacasablanca.backend.model.EventoPedido(
                            p, estadoAnterior, estadoNuevo, u, motivo
                    ));
                }
            }
        } catch (Exception ignored) {
            // No interrumpir la transacción principal si la auditoría en BD falla
        }
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
                List<ModificadorItemDto> mods = obtenerModificadoresDeDetalle(dp);
                detalleResponses.add(new DetallePedidoResponse(
                        dp.getId(),
                        dp.getProducto().getId(),
                        dp.getProducto().getNombre(),
                        dp.getProducto().getEstacion(),
                        dp.getPrecioUnitario(),
                        dp.getCantidad(),
                        dp.getNotas(),
                        dp.getSubtotal(),
                        mods
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
            String clientReq = guardado.getClientRequestId() != null
                    ? guardado.getClientRequestId()
                    : pedidoIdAClientRequestId.get(guardado.getId());
            response.setClientRequestId(clientReq);

            String motAnul = guardado.getMotivoAnulacion() != null
                    ? guardado.getMotivoAnulacion()
                    : motivosAnulacion.get(guardado.getId());
            response.setMotivoAnulacion(motAnul);

            List<EventoPedidoDto> eventos = eventosPorPedido.get(guardado.getId());
            if ((eventos == null || eventos.isEmpty()) && eventoPedidoRepository != null) {
                try {
                    List<com.lacasablanca.backend.model.EventoPedido> evsDb =
                            eventoPedidoRepository.findByPedidoIdOrderByFechaAsc(guardado.getId());
                    if (!evsDb.isEmpty()) {
                        eventos = evsDb.stream().map(e -> new EventoPedidoDto(
                                e.getEstadoAnterior(),
                                e.getEstadoNuevo(),
                                e.getUsuario() != null ? e.getUsuario().getUsername() : "sistema",
                                e.getMotivo(),
                                e.getFecha()
                        )).collect(Collectors.toList());
                    }
                } catch (Exception e) {
                    org.slf4j.LoggerFactory.getLogger(PedidoService.class)
                            .debug("Error consultando eventos en BD: {}", e.getMessage());
                }
            }
            response.setEventos(eventos != null ? new ArrayList<>(eventos) : List.of());

            boolean esParaLlevar = guardado.getMesa() == null;
            response.setParaLlevar(esParaLlevar);
            String tipo = guardado.getTipoPedido() != null
                    ? guardado.getTipoPedido()
                    : tipoPorPedidoId.getOrDefault(guardado.getId(), esParaLlevar ? "PARA_LLEVAR" : "SALON");
            response.setTipoPedido(tipo);

            String cliente = guardado.getClienteNombre() != null
                    ? guardado.getClienteNombre()
                    : clientePorPedidoId.get(guardado.getId());
            response.setClienteNombre(cliente);
        }
        return response;
    }

    public void limpiarCacheParaPruebas() {
        idempotenciaKeyAPedidoId.clear();
        pedidoActivoPorMesaId.clear();
        pedidoIdAClientRequestId.clear();
        motivosAnulacion.clear();
        eventosPorPedido.clear();
        modificadoresPorDetalleId.clear();
        tipoPorPedidoId.clear();
        clientePorPedidoId.clear();
    }
}
