package com.lacasablanca.backend.service;

import com.lacasablanca.backend.dto.CobrarRequest;
import com.lacasablanca.backend.dto.CobroResponse;
import com.lacasablanca.backend.dto.CuentaResponse;
import com.lacasablanca.backend.dto.ItemCuentaDto;
import com.lacasablanca.backend.dto.PagoRequestDto;
import com.lacasablanca.backend.dto.PedidoResumenCuentaDto;
import com.lacasablanca.backend.exception.ConflictoOperacionException;
import com.lacasablanca.backend.exception.RecursoNoEncontradoException;
import com.lacasablanca.backend.model.DetallePedido;
import com.lacasablanca.backend.model.EstadoMesa;
import com.lacasablanca.backend.model.EstadoPedido;
import com.lacasablanca.backend.model.Mesa;
import com.lacasablanca.backend.model.Pedido;
import com.lacasablanca.backend.repository.MesaRepository;
import com.lacasablanca.backend.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class CobroService {

    private static final Set<String> METODOS_VALIDOS = Set.of(
            "EFECTIVO", "YAPE", "PLIN", "TARJETA", "TRANSFERENCIA"
    );

    private final Map<String, CobroResponse> cobrosIdempotentes = new ConcurrentHashMap<>();

    private final MesaRepository mesaRepository;
    private final PedidoRepository pedidoRepository;
    private final MesaService mesaService;

    public CobroService(MesaRepository mesaRepository,
                        PedidoRepository pedidoRepository,
                        MesaService mesaService) {
        this.mesaRepository = mesaRepository;
        this.pedidoRepository = pedidoRepository;
        this.mesaService = mesaService;
    }

    @Transactional(readOnly = true)
    public CuentaResponse obtenerCuenta(Integer mesaId) {
        Mesa mesa = mesaRepository.findById(mesaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mesa no encontrada con ID: " + mesaId));

        if (mesa.getEstado() == EstadoMesa.LIBRE) {
            throw new ConflictoOperacionException("La mesa " + mesa.getNumero() + " está LIBRE y no tiene cuenta activa");
        }

        List<Pedido> pedidosActivos = pedidoRepository.findByMesaIdAndEstadoNotOrderByFechaCreacionAsc(
                mesaId, EstadoPedido.COBRADO
        );

        BigDecimal total = pedidosActivos.stream()
                .map(Pedido::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Desglose tributario Perú (D13 - IGV 18% incluido)
        BigDecimal subtotal = total.divide(BigDecimal.valueOf(1.18), 2, RoundingMode.HALF_UP);
        BigDecimal igv = total.subtract(subtotal);
        BigDecimal propinaSugerida = total.multiply(BigDecimal.valueOf(0.10)).setScale(2, RoundingMode.HALF_UP);

        // Consolidación de items por producto
        Map<Integer, ItemCuentaDto> itemsConsolidados = new LinkedHashMap<>();
        for (Pedido pedido : pedidosActivos) {
            if (pedido.getDetalles() != null) {
                for (DetallePedido detalle : pedido.getDetalles()) {
                    Integer prodId = detalle.getProducto().getId();
                    String nombre = detalle.getProducto().getNombre();
                    BigDecimal precio = detalle.getPrecioUnitario();
                    int cantidad = detalle.getCantidad();
                    BigDecimal subtotalLinea = detalle.getSubtotal();

                    if (itemsConsolidados.containsKey(prodId)) {
                        ItemCuentaDto existente = itemsConsolidados.get(prodId);
                        existente.setCantidad(existente.getCantidad() + cantidad);
                        existente.setSubtotalLinea(existente.getSubtotalLinea().add(subtotalLinea));
                    } else {
                        itemsConsolidados.put(prodId, new ItemCuentaDto(nombre, cantidad, precio, subtotalLinea));
                    }
                }
            }
        }

        List<PedidoResumenCuentaDto> pedidosDto = pedidosActivos.stream()
                .map(p -> new PedidoResumenCuentaDto(p.getId(), p.getEstado().name(), p.getTotal()))
                .collect(Collectors.toList());

        return new CuentaResponse(
                mesa.getId(),
                pedidosDto,
                new ArrayList<>(itemsConsolidados.values()),
                subtotal,
                igv,
                total,
                propinaSugerida
        );
    }

    @Transactional
    public CobroResponse cobrar(Integer mesaId, CobrarRequest request, String identificadorUsuario) {
        validarSolicitudCobro(request);

        String claveIdempotencia = normalizarClaveIdempotencia("MESA:" + mesaId, request.getClientRequestId());
        if (claveIdempotencia != null && cobrosIdempotentes.containsKey(claveIdempotencia)) {
            return cobrosIdempotentes.get(claveIdempotencia);
        }

        Mesa mesa = mesaRepository.findById(mesaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mesa no encontrada con ID: " + mesaId));

        if (mesa.getEstado() == EstadoMesa.LIBRE) {
            throw new ConflictoOperacionException("La mesa " + mesa.getNumero() + " ya se encuentra en estado LIBRE");
        }

        List<Pedido> pedidosActivos = pedidoRepository.findByMesaIdAndEstadoNotOrderByFechaCreacionAsc(
                mesaId, EstadoPedido.COBRADO
        );

        BigDecimal totalConsumo = pedidosActivos.stream()
                .map(Pedido::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ResultadoValidacionPagos validacion = validarYCalcularPagos(totalConsumo, request);

        // 1. Transicionar todos los pedidos activos a COBRADO
        List<PedidoResumenCuentaDto> pedidosCobrados = new ArrayList<>();
        for (Pedido p : pedidosActivos) {
            p.setEstado(EstadoPedido.COBRADO);
            pedidoRepository.save(p);
            pedidosCobrados.add(new PedidoResumenCuentaDto(p.getId(), p.getEstado().name(), p.getTotal()));
        }

        // 2. Liberar la mesa y registrar fecha de cierre
        mesa.setEstado(EstadoMesa.LIBRE);
        mesa.setFechaCierre(LocalDateTime.now());
        mesa.setMozo(null);
        mesaRepository.save(mesa);

        CobroResponse respuesta = new CobroResponse(
                totalConsumo,
                validacion.totalPagado,
                validacion.vuelto,
                pedidosCobrados
        );

        if (claveIdempotencia != null) {
            cobrosIdempotentes.put(claveIdempotencia, respuesta);
        }
        return respuesta;
    }

    @Transactional
    public CobroResponse cobrarPedido(Integer pedidoId, CobrarRequest request, String identificadorUsuario) {
        validarSolicitudCobro(request);

        String claveIdempotencia = normalizarClaveIdempotencia("PEDIDO:" + pedidoId, request.getClientRequestId());
        if (claveIdempotencia != null && cobrosIdempotentes.containsKey(claveIdempotencia)) {
            return cobrosIdempotentes.get(claveIdempotencia);
        }

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido no encontrado con ID: " + pedidoId));

        if (pedido.getEstado() == EstadoPedido.COBRADO) {
            throw new ConflictoOperacionException("El pedido " + pedidoId + " ya se encuentra COBRADO");
        }

        BigDecimal totalConsumo = pedido.getTotal();
        ResultadoValidacionPagos validacion = validarYCalcularPagos(totalConsumo, request);

        pedido.setEstado(EstadoPedido.COBRADO);
        pedidoRepository.save(pedido);

        Mesa mesa = pedido.getMesa();
        if (mesa != null) {
            List<Pedido> pendientesMesa = pedidoRepository.findByMesaIdAndEstadoNotOrderByFechaCreacionAsc(
                    mesa.getId(), EstadoPedido.COBRADO
            );
            if (pendientesMesa.isEmpty()) {
                mesa.setEstado(EstadoMesa.LIBRE);
                mesa.setFechaCierre(LocalDateTime.now());
                mesa.setMozo(null);
                mesaRepository.save(mesa);
            }
        }

        List<PedidoResumenCuentaDto> pedidosCobrados = List.of(
                new PedidoResumenCuentaDto(pedido.getId(), pedido.getEstado().name(), pedido.getTotal())
        );

        CobroResponse respuesta = new CobroResponse(
                totalConsumo,
                validacion.totalPagado,
                validacion.vuelto,
                pedidosCobrados
        );

        if (claveIdempotencia != null) {
            cobrosIdempotentes.put(claveIdempotencia, respuesta);
        }
        return respuesta;
    }

    @Transactional
    public CuentaResponse solicitarPrecuenta(Integer mesaId, String identificadorUsuario) {
        Mesa mesa = mesaRepository.findById(mesaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mesa no encontrada con ID: " + mesaId));

        if (mesa.getEstado() == EstadoMesa.LIBRE) {
            throw new ConflictoOperacionException("La mesa " + mesa.getNumero() + " está LIBRE");
        }

        // Si está en OCUPADA, pasa formalmente a CUENTA_SOLICITADA
        if (mesa.getEstado() == EstadoMesa.OCUPADA) {
            mesaService.actualizarEstado(mesaId, EstadoMesa.CUENTA_SOLICITADA, identificadorUsuario);
        }

        return obtenerCuenta(mesaId);
    }

    private void validarSolicitudCobro(CobrarRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("El cuerpo de la solicitud de cobro es obligatorio");
        }
        if (request.getPagos() == null || request.getPagos().isEmpty()) {
            throw new IllegalArgumentException("Debe registrar al menos un método de pago");
        }
    }

    private String normalizarClaveIdempotencia(String prefijo, String clientRequestId) {
        if (clientRequestId == null || clientRequestId.isBlank()) {
            return null;
        }
        return prefijo + ":" + clientRequestId.trim();
    }

    private ResultadoValidacionPagos validarYCalcularPagos(BigDecimal totalConsumo, CobrarRequest request) {
        BigDecimal propina = (request.getPropina() != null) ? request.getPropina() : BigDecimal.ZERO;
        if (propina.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("La propina no puede ser negativa");
        }

        BigDecimal totalLiquidacion = totalConsumo.add(propina);

        BigDecimal totalPagado = BigDecimal.ZERO;
        boolean tieneEfectivo = false;

        for (PagoRequestDto pago : request.getPagos()) {
            String metodoRaw = pago.getMetodo();
            String metodo = (metodoRaw != null) ? metodoRaw.toUpperCase(Locale.ROOT) : "";
            if (!METODOS_VALIDOS.contains(metodo)) {
                throw new IllegalArgumentException("Método de pago no válido: " + metodoRaw);
            }
            if (pago.getMonto() == null || pago.getMonto().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("El monto de cada pago debe ser mayor a cero");
            }
            if (!"EFECTIVO".equals(metodo)) {
                if (pago.getReferencia() == null || pago.getReferencia().trim().isEmpty()) {
                    throw new IllegalArgumentException(
                            "El número de operación o referencia es obligatorio para pagos con " + metodo
                    );
                }
            } else {
                tieneEfectivo = true;
            }
            totalPagado = totalPagado.add(pago.getMonto());
        }

        if (totalPagado.compareTo(totalLiquidacion) < 0) {
            throw new IllegalArgumentException("Monto pagado insuficiente. Total a liquidar: "
                    + totalLiquidacion + ", Total recibido: " + totalPagado);
        }

        BigDecimal vuelto = totalPagado.subtract(totalLiquidacion);
        if (vuelto.compareTo(BigDecimal.ZERO) > 0 && !tieneEfectivo) {
            throw new IllegalArgumentException("No se puede generar vuelto en pagos electrónicos directos");
        }

        return new ResultadoValidacionPagos(totalPagado, vuelto);
    }

    private static final class ResultadoValidacionPagos {
        private final BigDecimal totalPagado;
        private final BigDecimal vuelto;

        private ResultadoValidacionPagos(BigDecimal totalPagado, BigDecimal vuelto) {
            this.totalPagado = totalPagado;
            this.vuelto = vuelto;
        }
    }
}
