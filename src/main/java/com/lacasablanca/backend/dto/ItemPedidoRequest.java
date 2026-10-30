package com.lacasablanca.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class ItemPedidoRequest {

    @NotNull(message = "El producto_id es obligatorio")
    @JsonProperty("producto_id")
    private Integer productoId;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser mayor o igual a 1")
    private Integer cantidad;

    private String notas;

    public ItemPedidoRequest() {}

    public ItemPedidoRequest(Integer productoId, Integer cantidad, String notas) {
        this.productoId = productoId;
        this.cantidad = cantidad;
        this.notas = notas;
    }

    public Integer getProductoId() { return productoId; }
    public void setProductoId(Integer productoId) { this.productoId = productoId; }

    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }

    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }
}
