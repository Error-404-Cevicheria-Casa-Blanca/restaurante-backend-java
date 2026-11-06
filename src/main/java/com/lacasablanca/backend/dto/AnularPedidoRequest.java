package com.lacasablanca.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class AnularPedidoRequest {

    @NotBlank(message = "El motivo de anulación es obligatorio")
    private String motivo;

    public AnularPedidoRequest() {
    }

    public AnularPedidoRequest(String motivo) {
        this.motivo = motivo;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }
}
