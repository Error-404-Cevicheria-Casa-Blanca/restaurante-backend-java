package com.lacasablanca.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "pago")
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "mesa_id", nullable = false)
    private Mesa mesa;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "mozo_id")
    private Usuario mozo;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sesion_caja_id")
    private SesionCaja sesionCaja;

    @Column(name = "metodo", nullable = false, length = 20)
    private String metodo;

    @Column(name = "monto", nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(name = "propina", nullable = false, precision = 10, scale = 2)
    private BigDecimal propina = BigDecimal.ZERO;

    @Column(name = "referencia", length = 100)
    private String referencia;

    @Column(name = "client_request_id", nullable = false, unique = true, length = 64)
    private String clientRequestId;

    @Column(name = "fecha_pago", nullable = false)
    private OffsetDateTime fechaPago = OffsetDateTime.now();

    public Pago() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public void setPedido(Pedido pedido) {
        this.pedido = pedido;
    }

    public Mesa getMesa() {
        return mesa;
    }

    public void setMesa(Mesa mesa) {
        this.mesa = mesa;
    }

    public Usuario getMozo() {
        return mozo;
    }

    public void setMozo(Usuario mozo) {
        this.mozo = mozo;
    }

    public SesionCaja getSesionCaja() {
        return sesionCaja;
    }

    public void setSesionCaja(SesionCaja sesionCaja) {
        this.sesionCaja = sesionCaja;
    }

    public String getMetodo() {
        return metodo;
    }

    public void setMetodo(String metodo) {
        this.metodo = metodo;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public BigDecimal getPropina() {
        return propina;
    }

    public void setPropina(BigDecimal propina) {
        this.propina = propina;
    }

    public String getReferencia() {
        return referencia;
    }

    public void setReferencia(String referencia) {
        this.referencia = referencia;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }

    public OffsetDateTime getFechaPago() {
        return fechaPago;
    }

    public void setFechaPago(OffsetDateTime fechaPago) {
        this.fechaPago = fechaPago;
    }
}
