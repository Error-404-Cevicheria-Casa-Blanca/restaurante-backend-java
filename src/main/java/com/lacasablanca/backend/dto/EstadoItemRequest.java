package com.lacasablanca.backend.dto;

import com.lacasablanca.backend.model.EstadoItem;
import jakarta.validation.constraints.NotNull;

/** Cuerpo de PATCH /api/v1/pedidos/{pedidoId}/items/{itemId}/estado (PRB-87). */
public class EstadoItemRequest {

    @NotNull(message = "El estado es obligatorio")
    private EstadoItem estado;

    public EstadoItemRequest() {
    }

    public EstadoItemRequest(EstadoItem estado) {
        this.estado = estado;
    }

    public EstadoItem getEstado() {
        return estado;
    }

    public void setEstado(EstadoItem estado) {
        this.estado = estado;
    }
}
