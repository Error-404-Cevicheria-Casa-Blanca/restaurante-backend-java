package com.lacasablanca.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class DivisionMesaResponse {

    @JsonProperty("mesa_origen_id")
    private Integer mesaOrigenId;

    @JsonProperty("mesa_destino_id")
    private Integer mesaDestinoId;

    @JsonProperty("pedido_origen")
    private PedidoResponse pedidoOrigen;

    @JsonProperty("pedido_nuevo")
    private PedidoResponse pedidoNuevo;

    @JsonProperty("total_combinado")
    private BigDecimal totalCombinado;

    public DivisionMesaResponse() {
    }

    public DivisionMesaResponse(Integer mesaOrigenId,
                                Integer mesaDestinoId,
                                PedidoResponse pedidoOrigen,
                                PedidoResponse pedidoNuevo,
                                BigDecimal totalCombinado) {
        this.mesaOrigenId = mesaOrigenId;
        this.mesaDestinoId = mesaDestinoId;
        this.pedidoOrigen = pedidoOrigen;
        this.pedidoNuevo = pedidoNuevo;
        this.totalCombinado = totalCombinado;
    }

    public Integer getMesaOrigenId() {
        return mesaOrigenId;
    }

    public void setMesaOrigenId(Integer mesaOrigenId) {
        this.mesaOrigenId = mesaOrigenId;
    }

    public Integer getMesaDestinoId() {
        return mesaDestinoId;
    }

    public void setMesaDestinoId(Integer mesaDestinoId) {
        this.mesaDestinoId = mesaDestinoId;
    }

    public PedidoResponse getPedidoOrigen() {
        return pedidoOrigen;
    }

    public void setPedidoOrigen(PedidoResponse pedidoOrigen) {
        this.pedidoOrigen = pedidoOrigen;
    }

    public PedidoResponse getPedidoNuevo() {
        return pedidoNuevo;
    }

    public void setPedidoNuevo(PedidoResponse pedidoNuevo) {
        this.pedidoNuevo = pedidoNuevo;
    }

    public BigDecimal getTotalCombinado() {
        return totalCombinado;
    }

    public void setTotalCombinado(BigDecimal totalCombinado) {
        this.totalCombinado = totalCombinado;
    }
}
