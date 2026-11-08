package com.lacasablanca.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class ModificadorItemDto {

    private String nombre;

    @JsonProperty("precio_adicional")
    private BigDecimal precioAdicional = BigDecimal.ZERO;

    public ModificadorItemDto() {
    }

    public ModificadorItemDto(String nombre) {
        this.nombre = nombre;
        this.precioAdicional = BigDecimal.ZERO;
    }

    public ModificadorItemDto(String nombre, BigDecimal precioAdicional) {
        this.nombre = nombre;
        this.precioAdicional = precioAdicional != null ? precioAdicional : BigDecimal.ZERO;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public BigDecimal getPrecioAdicional() {
        return precioAdicional;
    }

    public void setPrecioAdicional(BigDecimal precioAdicional) {
        this.precioAdicional = precioAdicional != null ? precioAdicional : BigDecimal.ZERO;
    }
}
