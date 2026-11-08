package com.lacasablanca.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lacasablanca.backend.model.Estacion;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class DetallePedidoResponse {

    private Integer id;

    @JsonProperty("producto_id")
    private Integer productoId;

    @JsonProperty("producto_nombre")
    private String productoNombre;

    private Estacion estacion;

    @JsonProperty("precio_unitario")
    private BigDecimal precioUnitario;

    private Integer cantidad;
    private String notas;
    private BigDecimal subtotal;
    private List<ModificadorItemDto> modificadores = new ArrayList<>();

    public DetallePedidoResponse() {
    }

    public DetallePedidoResponse(Integer id,
                                 Integer productoId,
                                 String productoNombre,
                                 Estacion estacion,
                                 BigDecimal precioUnitario,
                                 Integer cantidad,
                                 String notas,
                                 BigDecimal subtotal) {
        this.id = id;
        this.productoId = productoId;
        this.productoNombre = productoNombre;
        this.estacion = estacion;
        this.precioUnitario = precioUnitario;
        this.cantidad = cantidad;
        this.notas = notas;
        this.subtotal = subtotal;
    }

    public DetallePedidoResponse(Integer id,
                                 Integer productoId,
                                 String productoNombre,
                                 Estacion estacion,
                                 BigDecimal precioUnitario,
                                 Integer cantidad,
                                 String notas,
                                 BigDecimal subtotal,
                                 List<ModificadorItemDto> modificadores) {
        this(id, productoId, productoNombre, estacion, precioUnitario, cantidad, notas, subtotal);
        this.modificadores = modificadores != null ? modificadores : new ArrayList<>();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getProductoId() {
        return productoId;
    }

    public void setProductoId(Integer productoId) {
        this.productoId = productoId;
    }

    public String getProductoNombre() {
        return productoNombre;
    }

    public void setProductoNombre(String productoNombre) {
        this.productoNombre = productoNombre;
    }

    public Estacion getEstacion() {
        return estacion;
    }

    public void setEstacion(Estacion estacion) {
        this.estacion = estacion;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }

    public void setPrecioUnitario(BigDecimal precioUnitario) {
        this.precioUnitario = precioUnitario;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public void setCantidad(Integer cantidad) {
        this.cantidad = cantidad;
    }

    public String getNotas() {
        return notas;
    }

    public void setNotas(String notas) {
        this.notas = notas;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public List<ModificadorItemDto> getModificadores() {
        return modificadores;
    }

    public void setModificadores(List<ModificadorItemDto> modificadores) {
        this.modificadores = modificadores;
    }
}
