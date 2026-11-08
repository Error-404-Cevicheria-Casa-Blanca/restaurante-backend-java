package com.lacasablanca.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ItemCuentaDto {

    private String nombre;
    private Integer cantidad;

    @JsonProperty("precio_unitario")
    private BigDecimal precioUnitario;

    @JsonProperty("subtotal_linea")
    private BigDecimal subtotalLinea;

    private List<ModificadorItemDto> modificadores = new ArrayList<>();

    public ItemCuentaDto() {
    }

    public ItemCuentaDto(String nombre, Integer cantidad, BigDecimal precioUnitario, BigDecimal subtotalLinea) {
        this.nombre = nombre;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.subtotalLinea = subtotalLinea;
    }

    public ItemCuentaDto(String nombre,
                         Integer cantidad,
                         BigDecimal precioUnitario,
                         BigDecimal subtotalLinea,
                         List<ModificadorItemDto> modificadores) {
        this(nombre, cantidad, precioUnitario, subtotalLinea);
        this.modificadores = modificadores != null ? modificadores : new ArrayList<>();
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public void setCantidad(Integer cantidad) {
        this.cantidad = cantidad;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }

    public void setPrecioUnitario(BigDecimal precioUnitario) {
        this.precioUnitario = precioUnitario;
    }

    public BigDecimal getSubtotalLinea() {
        return subtotalLinea;
    }

    public void setSubtotalLinea(BigDecimal subtotalLinea) {
        this.subtotalLinea = subtotalLinea;
    }

    public List<ModificadorItemDto> getModificadores() {
        return modificadores;
    }

    public void setModificadores(List<ModificadorItemDto> modificadores) {
        this.modificadores = modificadores;
    }
}
