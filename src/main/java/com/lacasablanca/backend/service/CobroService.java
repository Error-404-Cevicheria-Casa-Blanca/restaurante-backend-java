package com.lacasablanca.backend.service;

import com.lacasablanca.backend.dto.CobrarRequest;
import com.lacasablanca.backend.dto.CobroResponse;
import com.lacasablanca.backend.dto.CuentaResponse;
import com.lacasablanca.backend.dto.ItemCuentaDto;
import com.lacasablanca.backend.dto.ModificadorItemDto;
import com.lacasablanca.backend.dto.PagoRequestDto;
import com.lacasablanca.backend.dto.PedidoResumenCuentaDto;
import com.lacasablanca.backend.dto.TrabajoImpresionDto;
import com.lacasablanca.backend.exception.ConflictoOperacionException;
import com.lacasablanca.backend.exception.RecursoNoEncontradoException;
import com.lacasablanca.backend.model.DetallePedido;
import com.lacasablanca.backend.model.EstadoMesa;
import com.lacasablanca.backend.model.EstadoPedido;
import com.lacasablanca.backend.model.Mesa;
import com.lacasablanca.backend.model.Pago;
import com.lacasablanca.backend.model.Pedido;
import com.lacasablanca.backend.model.SesionCaja;
import com.lacasablanca.backend.repository.MesaRepository;
import com.lacasablanca.backend.repository.PagoRepository;
import com.lacasablanca.backend.repository.PedidoRepository;
import com.lacasablanca.backend.repository.SesionCajaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class CobroService {

    private static final Set<String> METODOS_VALIDOS = Set.of(
            "EFECTIVO", "YAPE", "PLIN", "TARJETA", "TRANSFERENCIA"
    );

    private final Map<String, CobroResponse> cobrosIdempotentes = new ConcurrentHashMap<>();
    private final Map<Integer, TrabajoImpresionDto> trabajosImpresionMesa = new ConcurrentHashMap<>();

    private final MesaRepository mesaRepository;
    private final PedidoRepository pedidoRepository;
    private final PagoRepository pagoRepository;
    private final SesionCajaRepository sesionCajaRepository;
    private final com.lacasablanca.backend.repository.TrabajoImpresionRepository trabajoImpresionRepository;
    private final MesaService mesaService;
    private final PedidoService pedidoService;

    public CobroService(MesaRepository mesaRepository,
                         PedidoRepository pedidoRepository,
                         PagoRepository pagoRepository,
                         SesionCajaRepository sesionCajaRepository,
                         com.lacasablanca.backend.repository.TrabajoImpresionRepository trabajoImpresionRepository,
                         MesaService mesaService,
                         PedidoService pedidoService) {
        this.mesaRepository = mesaRepository;
        this.pedidoRepository = pedidoRepository;
        this.pagoRepository = pagoRepository;
        this.sesionCajaRepository = sesionCajaRepository;
        this.trabajoImpresionRepository = trabajoImpresionRepository;
        this.mesaService = mesaService;
        this.pedidoService = pedidoService;
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
        ).stream().filter(p -> p.getEstado() != EstadoPedido.ANULADO).collect(Collectors.toList());

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

                    List<ModificadorItemDto> mods = pedidoService.obtenerModificadoresDeDetalle(detalle);
                    if (itemsConsolidados.containsKey(prodId)) {
                        ItemCuentaDto existente = itemsConsolidados.get(prodId);
                        existente.setCantidad(existente.getCantidad() + cantidad);
                        existente.setSubtotalLinea(existente.getSubtotalLinea().add(subtotalLinea));
                        if (!mods.isEmpty()) {
                            List<ModificadorItemDto> acumulados = new ArrayList<>(existente.getModificadores());
                            acumulados.addAll(mods);
                            existente.setModificadores(acumulados);
                        }
                    } else {
                        itemsConsolidados.put(
                                prodId,
                                new ItemCuentaDto(nombre, cantidad, precio, subtotalLinea, mods)
                        );
                    }
                }
            }
        }

        List<PedidoResumenCuentaDto> pedidosDto = pedidosActivos.stream()
                .map(p -> new PedidoResumenCuentaDto(p.getId(), p.getEstado().name(), p.getTotal()))
                .collect(Collectors.toList());

        CuentaResponse response = new CuentaResponse(
                mesa.getId(),
                pedidosDto,
                new ArrayList<>(itemsConsolidados.values()),
                subtotal,
                igv,
                total,
                propinaSugerida
        );
        TrabajoImpresionDto job = trabajosImpresionMesa.get(mesaId);
        if (job == null && trabajoImpresionRepository != null) {
            try {
                job = trabajoImpresionRepository.findAll().stream()
                        .filter(ti -> ("Mesa " + mesaId + " precuenta").equals(ti.getPayload()))
                        .max(java.util.Comparator.comparing(com.lacasablanca.backend.model.TrabajoImpresion::getFechaCreacion))
                        .map(ti -> new TrabajoImpresionDto(
                                "print-mesa-" + mesaId + "-" + ti.getIntentos(),
                                mesaId,
                                "PRECUENTA_80MM",
                                ti.getEstado(),
                                ti.getIntentos(),
                                ti.getError(),
                                ti.getFechaCreacion()
                        )).orElse(null);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(CobroService.class)
                        .debug("Error consultando cola impresion en BD: {}", e.getMessage());
            }
        }
        if (job != null) {
            String estadoImpresion = "FALLIDO".equals(job.getEstado()) ? "ERROR" : job.getEstado();
            response.setImpresionJobId(job.getJobId());
            response.setEstadoImpresion(estadoImpresion);
            response.setIntentosImpresion(job.getIntentos());
        }
        return response;
    }

    @Transactional
    public CobroResponse cobrar(Integer mesaId, CobrarRequest request, String identificadorUsuario) {
        validarSolicitudCobro(request);

        String claveIdempotencia = normalizarClaveIdempotencia("MESA:" + mesaId, request.getClientRequestId());
        if (claveIdempotencia != null) {
            if (cobrosIdempotentes.containsKey(claveIdempotencia)) {
                return cobrosIdempotentes.get(claveIdempotencia);
            }
            if (pagoRepository.findByClientRequestId(claveIdempotencia).isPresent()
                    || pagoRepository.findByClientRequestId(request.getClientRequestId()).isPresent()) {
                return reconstruirCobroResponseDesdeDb(mesaId, request.getClientRequestId());
            }
        }

        Mesa mesa = mesaRepository.findById(mesaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mesa no encontrada con ID: " + mesaId));

        if (mesa.getEstado() == EstadoMesa.LIBRE) {
            throw new ConflictoOperacionException("La mesa " + mesa.getNumero() + " ya se encuentra en estado LIBRE");
        }

        List<Pedido> pedidosActivos = pedidoRepository.findByMesaIdAndEstadoNotOrderByFechaCreacionAsc(
                mesaId, EstadoPedido.COBRADO
        ).stream().filter(p -> p.getEstado() != EstadoPedido.ANULADO).collect(Collectors.toList());

        BigDecimal totalConsumo = pedidosActivos.stream()
                .map(Pedido::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ResultadoValidacionPagos validacion = validarYCalcularPagos(totalConsumo, request);

        // 1. Persistir registros en la tabla 'pago' y actualizar 'sesion_caja' si está abierta
        if (!pedidosActivos.isEmpty()) {
            registrarPagosEnBaseDeDatos(pedidosActivos, mesa, request, validacion.vuelto);
        }

        // 2. Transicionar todos los pedidos activos a COBRADO
        List<PedidoResumenCuentaDto> pedidosCobrados = new ArrayList<>();
        for (Pedido p : pedidosActivos) {
            p.setEstado(EstadoPedido.COBRADO);
            pedidoRepository.save(p);
            pedidosCobrados.add(new PedidoResumenCuentaDto(p.getId(), p.getEstado().name(), p.getTotal()));
            if (validacion.montoDescuento.compareTo(BigDecimal.ZERO) > 0) {
                String descTxt = "Descuento de S/ " + validacion.montoDescuento
                        + (request.getPorcentajeDescuento() != null ? " (" + request.getPorcentajeDescuento() + "%)" : "")
                        + " aplicado por " + (identificadorUsuario != null ? identificadorUsuario : "sistema");
                pedidoService.registrarEvento(p.getId(), EstadoPedido.COBRADO, EstadoPedido.COBRADO,
                        identificadorUsuario, descTxt);
            }
        }

        // 3. Liberar la mesa y registrar fecha de cierre
        mesa.setEstado(EstadoMesa.LIBRE);
        mesa.setFechaCierre(LocalDateTime.now());
        mesa.setMozo(null);
        mesaRepository.save(mesa);

        CobroResponse respuesta = new CobroResponse(
                validacion.totalConDescuento,
                validacion.subtotalNeto,
                validacion.igv,
                validacion.montoDescuento,
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
        if (claveIdempotencia != null) {
            if (cobrosIdempotentes.containsKey(claveIdempotencia)) {
                return cobrosIdempotentes.get(claveIdempotencia);
            }
            if (pagoRepository.findByClientRequestId(claveIdempotencia).isPresent()
                    || pagoRepository.findByClientRequestId(request.getClientRequestId()).isPresent()) {
                return reconstruirCobroResponsePedidoDesdeDb(pedidoId);
            }
        }

        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pedido no encontrado con ID: " + pedidoId));

        if (pedido.getEstado() == EstadoPedido.COBRADO) {
            throw new ConflictoOperacionException("El pedido " + pedidoId + " ya se encuentra COBRADO");
        }

        BigDecimal totalConsumo = pedido.getTotal();
        ResultadoValidacionPagos validacion = validarYCalcularPagos(totalConsumo, request);

        Mesa mesa = pedido.getMesa();
        registrarPagosEnBaseDeDatos(pedido, mesa, request, validacion.vuelto);

        pedido.setEstado(EstadoPedido.COBRADO);
        pedidoRepository.save(pedido);

        if (mesa != null) {
            List<Pedido> pendientesMesa = pedidoRepository.findByMesaIdAndEstadoNotOrderByFechaCreacionAsc(
                    mesa.getId(), EstadoPedido.COBRADO
            ).stream().filter(p -> p.getEstado() != EstadoPedido.ANULADO).collect(Collectors.toList());
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

        if (validacion.montoDescuento.compareTo(BigDecimal.ZERO) > 0) {
            String descTxt = "Descuento de S/ " + validacion.montoDescuento
                    + (request.getPorcentajeDescuento() != null ? " (" + request.getPorcentajeDescuento() + "%)" : "")
                    + " aplicado por " + (identificadorUsuario != null ? identificadorUsuario : "sistema");
            pedidoService.registrarEvento(pedido.getId(), EstadoPedido.COBRADO, EstadoPedido.COBRADO,
                    identificadorUsuario, descTxt);
        }

        CobroResponse respuesta = new CobroResponse(
                validacion.totalConDescuento,
                validacion.subtotalNeto,
                validacion.igv,
                validacion.montoDescuento,
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

        registrarTrabajoImpresion(mesaId, false);
        return obtenerCuenta(mesaId);
    }

    @Transactional
    public TrabajoImpresionDto reimprimirPrecuenta(Integer mesaId,
                                                   boolean simularError,
                                                   String identificadorUsuario) {
        Mesa mesa = mesaRepository.findById(mesaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mesa no encontrada con ID: " + mesaId));

        if (mesa.getEstado() == EstadoMesa.LIBRE) {
            throw new ConflictoOperacionException("La mesa " + mesa.getNumero() + " está LIBRE");
        }

        return registrarTrabajoImpresion(mesaId, simularError);
    }

    @Transactional(readOnly = true)
    public TrabajoImpresionDto obtenerEstadoImpresion(Integer mesaId) {
        Mesa mesa = mesaRepository.findById(mesaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mesa no encontrada con ID: " + mesaId));

        TrabajoImpresionDto existente = trabajosImpresionMesa.get(mesa.getId());
        if (existente == null && trabajoImpresionRepository != null) {
            try {
                existente = trabajoImpresionRepository.findAll().stream()
                        .filter(ti -> ("Mesa " + mesaId + " precuenta").equals(ti.getPayload()))
                        .max(java.util.Comparator.comparing(com.lacasablanca.backend.model.TrabajoImpresion::getFechaCreacion))
                        .map(ti -> new TrabajoImpresionDto(
                                "print-mesa-" + mesaId + "-" + ti.getIntentos(),
                                mesaId,
                                "PRECUENTA_80MM",
                                ti.getEstado(),
                                ti.getIntentos(),
                                ti.getError(),
                                ti.getFechaCreacion()
                        )).orElse(null);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(CobroService.class)
                        .debug("Error consultando cola impresion en BD: {}", e.getMessage());
            }
        }
        if (existente != null) {
            String estadoImpresion = "FALLIDO".equals(existente.getEstado()) ? "ERROR" : existente.getEstado();
            existente.setEstado(estadoImpresion);
            return existente;
        }
        return new TrabajoImpresionDto(
                "job-mesa-" + mesa.getId() + "-pendiente",
                mesa.getId(),
                "PRECUENTA_80MM",
                "EN_COLA",
                0,
                null,
                OffsetDateTime.now()
        );
    }

    private TrabajoImpresionDto registrarTrabajoImpresion(Integer mesaId, boolean simularError) {
        TrabajoImpresionDto previo = trabajosImpresionMesa.get(mesaId);
        int intentos = (previo != null && previo.getIntentos() != null) ? previo.getIntentos() + 1 : 1;
        String jobId = "print-mesa-" + mesaId + "-" + intentos;
        String estadoDb = simularError ? "FALLIDO" : "IMPRESO";
        String estadoDto = simularError ? "ERROR" : "IMPRESO";
        String mensajeError = simularError
                ? "Impresora térmica 80mm sin respuesta (apagada o desconectada). Comanda y cuenta operativas."
                : null;
        TrabajoImpresionDto nuevo = new TrabajoImpresionDto(
                jobId,
                mesaId,
                "PRECUENTA_80MM",
                estadoDto,
                intentos,
                mensajeError,
                OffsetDateTime.now()
        );
        trabajosImpresionMesa.put(mesaId, nuevo);

        try {
            if (trabajoImpresionRepository != null) {
                com.lacasablanca.backend.model.TrabajoImpresion ti = new com.lacasablanca.backend.model.TrabajoImpresion(
                        null, "PRECUENTA", "CAJA_80MM", "Mesa " + mesaId + " precuenta", estadoDb
                );
                ti.setIntentos(intentos);
                ti.setError(mensajeError);
                trabajoImpresionRepository.save(ti);
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(CobroService.class)
                    .debug("Error registrando trabajo de impresion en BD: {}", e.getMessage());
        }
        return nuevo;
    }

    private void registrarPagosEnBaseDeDatos(Pedido pedido,
                                             Mesa mesa,
                                             CobrarRequest request,
                                             BigDecimal vueltoTotal) {
        registrarPagosEnBaseDeDatos(List.of(pedido), mesa, request, vueltoTotal);
    }

    private void registrarPagosEnBaseDeDatos(List<Pedido> pedidos,
                                             Mesa mesa,
                                             CobrarRequest request,
                                             BigDecimal vueltoTotal) {
        if (pedidos == null || pedidos.isEmpty()) {
            return;
        }
        SesionCaja sesionAbierta = sesionCajaRepository
                .findFirstByEstadoOrderByFechaAperturaDesc("ABIERTA")
                .orElse(null);

        BigDecimal propinaTotal = request.getPropina() != null ? request.getPropina() : BigDecimal.ZERO;
        String baseClientId = (request.getClientRequestId() != null && !request.getClientRequestId().isBlank())
                ? request.getClientRequestId().trim()
                : UUID.randomUUID().toString();

        BigDecimal vueltoRestante = vueltoTotal != null ? vueltoTotal : BigDecimal.ZERO;
        List<PagoRequestDto> listaPagos = request.getPagos();

        if (pedidos.size() == 1) {
            Pedido pedido = pedidos.get(0);
            for (int i = 0; i < listaPagos.size(); i++) {
                PagoRequestDto dto = listaPagos.get(i);
                guardarRegistroPagoIndividual(pedido, mesa, dto, i, baseClientId, vueltoRestante, (i == 0 ? propinaTotal : BigDecimal.ZERO), sesionAbierta);
                if ("EFECTIVO".equalsIgnoreCase(dto.getMetodo()) && vueltoRestante.compareTo(BigDecimal.ZERO) > 0) {
                    vueltoRestante = vueltoRestante.subtract(dto.getMonto().min(vueltoRestante));
                }
            }
            return;
        }

        // Si la mesa tiene múltiples pedidos, distribuimos los pagos entre ellos
        int pedidoIdx = 0;
        BigDecimal saldoPedidoActual = pedidos.get(0).getTotal();
        int subIndex = 0;

        for (int i = 0; i < listaPagos.size(); i++) {
            PagoRequestDto dto = listaPagos.get(i);
            BigDecimal montoPago = dto.getMonto();
            if ("EFECTIVO".equalsIgnoreCase(dto.getMetodo()) && vueltoRestante.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal desc = montoPago.min(vueltoRestante);
                montoPago = montoPago.subtract(desc);
                vueltoRestante = vueltoRestante.subtract(desc);
            }

            while (montoPago.compareTo(BigDecimal.ZERO) > 0) {
                Pedido pedidoDestino = pedidos.get(pedidoIdx);
                BigDecimal montoParaEstePedido;
                if (pedidoIdx < pedidos.size() - 1) {
                    montoParaEstePedido = montoPago.min(saldoPedidoActual);
                } else {
                    montoParaEstePedido = montoPago;
                }

                if (montoParaEstePedido.compareTo(BigDecimal.ZERO) <= 0) {
                    montoParaEstePedido = montoPago;
                }

                PagoRequestDto subDto = new PagoRequestDto(dto.getMetodo(), montoParaEstePedido, dto.getReferencia());
                BigDecimal propina = (subIndex == 0) ? propinaTotal : BigDecimal.ZERO;
                guardarRegistroPagoIndividual(pedidoDestino, mesa, subDto, subIndex++, baseClientId, BigDecimal.ZERO, propina, sesionAbierta);

                montoPago = montoPago.subtract(montoParaEstePedido);
                saldoPedidoActual = saldoPedidoActual.subtract(montoParaEstePedido);

                if (saldoPedidoActual.compareTo(BigDecimal.ZERO) <= 0 && pedidoIdx < pedidos.size() - 1) {
                    pedidoIdx++;
                    saldoPedidoActual = pedidos.get(pedidoIdx).getTotal();
                }
            }
        }
    }

    private void guardarRegistroPagoIndividual(Pedido pedido,
                                               Mesa mesa,
                                               PagoRequestDto dto,
                                               int index,
                                               String baseClientId,
                                               BigDecimal vueltoRestante,
                                               BigDecimal propina,
                                               SesionCaja sesionAbierta) {
        String metodoUpper = dto.getMetodo().trim().toUpperCase(Locale.ROOT);
        String metodoDb = "TRANSFERENCIA".equals(metodoUpper) ? "TARJETA" : metodoUpper;

        BigDecimal montoNeto = dto.getMonto();
        if ("EFECTIVO".equals(metodoDb) && vueltoRestante.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal descuentoVuelto = montoNeto.min(vueltoRestante);
            montoNeto = montoNeto.subtract(descuentoVuelto);
        }

        String itemClientId = (index == 0) ? baseClientId : baseClientId + "-" + index;
        if (itemClientId.length() > 64) {
            itemClientId = itemClientId.substring(0, 64);
        }
        if (pagoRepository.findByClientRequestId(itemClientId).isPresent()) {
            itemClientId = UUID.randomUUID().toString();
        }

        Pago pago = new Pago();
        pago.setPedido(pedido);
        pago.setMesa(mesa);
        pago.setMozo((mesa != null && mesa.getMozo() != null) ? mesa.getMozo() : pedido.getMozo());
        pago.setSesionCaja(sesionAbierta);
        pago.setMetodo(metodoDb);
        pago.setMonto(montoNeto.compareTo(BigDecimal.ZERO) > 0 ? montoNeto : dto.getMonto());
        pago.setPropina(propina);
        pago.setReferencia(dto.getReferencia());
        pago.setClientRequestId(itemClientId);
        pago.setFechaPago(OffsetDateTime.now());
        pagoRepository.save(pago);

        if (sesionAbierta != null) {
            if ("EFECTIVO".equals(metodoDb)) {
                BigDecimal nuevoEfectivo = sesionAbierta.getMontoVentasEfectivo().add(pago.getMonto());
                sesionAbierta.setMontoVentasEfectivo(nuevoEfectivo);
                sesionAbierta.setMontoEsperado(
                        sesionAbierta.getMontoInicial().add(nuevoEfectivo)
                );
            } else {
                BigDecimal nuevoDigital = sesionAbierta.getMontoVentasDigital().add(pago.getMonto());
                sesionAbierta.setMontoVentasDigital(nuevoDigital);
            }
            sesionCajaRepository.save(sesionAbierta);
        }
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

        BigDecimal montoDescuento = BigDecimal.ZERO;
        if (request.getPorcentajeDescuento() != null) {
            BigDecimal porcentaje = request.getPorcentajeDescuento();
            if (porcentaje.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("El porcentaje de descuento no puede ser negativo");
            }
            if (porcentaje.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new IllegalArgumentException("El porcentaje de descuento no puede ser mayor a 100%");
            }
            montoDescuento = totalConsumo.multiply(porcentaje)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else if (request.getDescuento() != null) {
            if (request.getDescuento().compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("El descuento no puede ser negativo");
            }
            montoDescuento = request.getDescuento().setScale(2, RoundingMode.HALF_UP);
        }

        if (montoDescuento.compareTo(totalConsumo) > 0) {
            throw new IllegalArgumentException("El descuento no puede ser mayor al total de la cuenta. Descuento: "
                    + montoDescuento + ", Total consumo: " + totalConsumo);
        }

        BigDecimal totalConDescuento = totalConsumo.subtract(montoDescuento).setScale(2, RoundingMode.HALF_UP);
        if (totalConDescuento.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("El total a liquidar no puede ser negativo");
        }

        BigDecimal subtotalNeto = totalConDescuento.divide(BigDecimal.valueOf(1.18), 2, RoundingMode.HALF_UP);
        BigDecimal igv = totalConDescuento.subtract(subtotalNeto);

        BigDecimal totalLiquidacion = totalConDescuento.add(propina);

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

        return new ResultadoValidacionPagos(
                totalPagado,
                vuelto,
                montoDescuento,
                totalConDescuento,
                subtotalNeto,
                igv
        );
    }

    private CobroResponse reconstruirCobroResponseDesdeDb(Integer mesaId, String clientReqId) {
        String patron = clientReqId != null ? clientReqId.trim() : "";
        List<Pago> pagosDb = pagoRepository.findAll().stream()
                .filter(p -> p.getClientRequestId() != null && (
                        p.getClientRequestId().equals(patron)
                                || p.getClientRequestId().startsWith(patron + "-")
                                || p.getClientRequestId().equals("MESA:" + mesaId + ":" + patron)
                ))
                .collect(Collectors.toList());
        if (pagosDb.isEmpty() && mesaId != null) {
            pagosDb = pagoRepository.findAll().stream()
                    .filter(p -> p.getMesa() != null && mesaId.equals(p.getMesa().getId()))
                    .collect(Collectors.toList());
        }

        BigDecimal totalPagado = pagosDb.stream().map(Pago::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal propinas = pagosDb.stream().map(Pago::getPropina).reduce(BigDecimal.ZERO, BigDecimal::add);

        Set<Integer> pedidoIds = pagosDb.stream().map(p -> p.getPedido().getId()).collect(Collectors.toSet());
        List<PedidoResumenCuentaDto> pedidos = pedidoRepository.findAllById(pedidoIds).stream()
                .map(p -> new PedidoResumenCuentaDto(p.getId(), p.getEstado().name(), p.getTotal()))
                .collect(Collectors.toList());
        BigDecimal totalConsumo = pedidos.stream().map(PedidoResumenCuentaDto::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalConsumo.compareTo(BigDecimal.ZERO) == 0) {
            totalConsumo = totalPagado.subtract(propinas).max(BigDecimal.ZERO);
        }

        BigDecimal subtotalNeto = totalConsumo.divide(BigDecimal.valueOf(1.18), 2, RoundingMode.HALF_UP);
        BigDecimal igv = totalConsumo.subtract(subtotalNeto);
        CobroResponse resp = new CobroResponse(totalConsumo, totalPagado, BigDecimal.ZERO, pedidos);
        resp.setSubtotal(subtotalNeto);
        resp.setIgv(igv);
        resp.setDescuento(BigDecimal.ZERO);
        return resp;
    }

    private CobroResponse reconstruirCobroResponsePedidoDesdeDb(Integer pedidoId) {
        Pedido p = pedidoRepository.findById(pedidoId).orElse(null);
        BigDecimal total = p != null && p.getTotal() != null ? p.getTotal() : BigDecimal.ZERO;
        List<PedidoResumenCuentaDto> resumen = p != null
                ? List.of(new PedidoResumenCuentaDto(p.getId(), p.getEstado().name(), p.getTotal()))
                : List.of();
        BigDecimal subtotalNeto = total.divide(BigDecimal.valueOf(1.18), 2, RoundingMode.HALF_UP);
        BigDecimal igv = total.subtract(subtotalNeto);
        CobroResponse resp = new CobroResponse(total, total, BigDecimal.ZERO, resumen);
        resp.setSubtotal(subtotalNeto);
        resp.setIgv(igv);
        resp.setDescuento(BigDecimal.ZERO);
        return resp;
    }

    public void limpiarCacheParaPruebas() {
        trabajosImpresionMesa.clear();
        cobrosIdempotentes.clear();
    }

    private static final class ResultadoValidacionPagos {
        private final BigDecimal totalPagado;
        private final BigDecimal vuelto;
        private final BigDecimal montoDescuento;
        private final BigDecimal totalConDescuento;
        private final BigDecimal subtotalNeto;
        private final BigDecimal igv;

        private ResultadoValidacionPagos(BigDecimal totalPagado, BigDecimal vuelto,
                                         BigDecimal montoDescuento, BigDecimal totalConDescuento,
                                         BigDecimal subtotalNeto, BigDecimal igv) {
            this.totalPagado = totalPagado;
            this.vuelto = vuelto;
            this.montoDescuento = montoDescuento;
            this.totalConDescuento = totalConDescuento;
            this.subtotalNeto = subtotalNeto;
            this.igv = igv;
        }
    }
}
