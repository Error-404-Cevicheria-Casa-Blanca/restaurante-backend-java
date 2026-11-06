package com.lacasablanca.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public class TransferirMesaRequest {

    @NotNull(message = "El mesa_destino_id es obligatorio para transferir")
    @JsonProperty("mesa_destino_id")
    private Integer mesaDestinoId;

    public TransferirMesaRequest() {
    }

    public TransferirMesaRequest(Integer mesaDestinoId) {
        this.mesaDestinoId = mesaDestinoId;
    }

    public Integer getMesaDestinoId() {
        return mesaDestinoId;
    }

    public void setMesaDestinoId(Integer mesaDestinoId) {
        this.mesaDestinoId = mesaDestinoId;
    }
}
