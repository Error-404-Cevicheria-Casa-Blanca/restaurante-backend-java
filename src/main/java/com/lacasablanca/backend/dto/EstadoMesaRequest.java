package com.lacasablanca.backend.dto;

import com.lacasablanca.backend.model.EstadoMesa;
import jakarta.validation.constraints.NotNull;

public class EstadoMesaRequest {

    @NotNull(message = "El estado es obligatorio")
    private EstadoMesa estado;

    public EstadoMesaRequest() {}

    public EstadoMesaRequest(EstadoMesa estado) {
        this.estado = estado;
    }

    public EstadoMesa getEstado() { return estado; }
    public void setEstado(EstadoMesa estado) { this.estado = estado; }
}
