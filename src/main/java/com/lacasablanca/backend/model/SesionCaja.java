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
@Table(name = "sesion_caja")
public class SesionCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cajero_id", nullable = false)
    private Usuario cajero;

    @Column(name = "fecha_apertura", nullable = false)
    private OffsetDateTime fechaApertura = OffsetDateTime.now();

    @Column(name = "fecha_cierre")
    private OffsetDateTime fechaCierre;

    @Column(name = "monto_inicial", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoInicial = BigDecimal.ZERO;

    @Column(name = "monto_ventas_efectivo", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoVentasEfectivo = BigDecimal.ZERO;

    @Column(name = "monto_ventas_digital", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoVentasDigital = BigDecimal.ZERO;

    @Column(name = "monto_esperado", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoEsperado = BigDecimal.ZERO;

    @Column(name = "monto_real", precision = 10, scale = 2)
    private BigDecimal montoReal;

    @Column(name = "diferencia", precision = 10, scale = 2)
    private BigDecimal diferencia;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = "ABIERTA";

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    public SesionCaja() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Usuario getCajero() {
        return cajero;
    }

    public void setCajero(Usuario cajero) {
        this.cajero = cajero;
    }

    public OffsetDateTime getFechaApertura() {
        return fechaApertura;
    }

    public void setFechaApertura(OffsetDateTime fechaApertura) {
        this.fechaApertura = fechaApertura;
    }

    public OffsetDateTime getFechaCierre() {
        return fechaCierre;
    }

    public void setFechaCierre(OffsetDateTime fechaCierre) {
        this.fechaCierre = fechaCierre;
    }

    public BigDecimal getMontoInicial() {
        return montoInicial;
    }

    public void setMontoInicial(BigDecimal montoInicial) {
        this.montoInicial = montoInicial;
    }

    public BigDecimal getMontoVentasEfectivo() {
        return montoVentasEfectivo;
    }

    public void setMontoVentasEfectivo(BigDecimal montoVentasEfectivo) {
        this.montoVentasEfectivo = montoVentasEfectivo;
    }

    public BigDecimal getMontoVentasDigital() {
        return montoVentasDigital;
    }

    public void setMontoVentasDigital(BigDecimal montoVentasDigital) {
        this.montoVentasDigital = montoVentasDigital;
    }

    public BigDecimal getMontoEsperado() {
        return montoEsperado;
    }

    public void setMontoEsperado(BigDecimal montoEsperado) {
        this.montoEsperado = montoEsperado;
    }

    public BigDecimal getMontoReal() {
        return montoReal;
    }

    public void setMontoReal(BigDecimal montoReal) {
        this.montoReal = montoReal;
    }

    public BigDecimal getDiferencia() {
        return diferencia;
    }

    public void setDiferencia(BigDecimal diferencia) {
        this.diferencia = diferencia;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }
}
