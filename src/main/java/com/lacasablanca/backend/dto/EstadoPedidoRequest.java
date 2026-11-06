package com.lacasablanca.backend.dto;

import com.lacasablanca.backend.model.EstadoPedido;
import jakarta.validation.constraints.NotNull;

public class EstadoPedidoRequest {

    @NotNull(message = "El estado es obligatorio")
    private EstadoPedido estado;

    private String motivo;

    public EstadoPedidoRequest() {
    }

    public EstadoPedidoRequest(EstadoPedido estado, String motivo) {
        this.estado = estado;
        this.motivo = motivo;
    }

    public EstadoPedido getEstado() {
        return estado;
    }

    public void setEstado(EstadoPedido estado) {
        this.estado = estado;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }
}
