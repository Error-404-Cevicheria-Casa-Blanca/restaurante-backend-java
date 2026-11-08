package com.lacasablanca.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public class FusionarMesasRequest {

    @NotNull(message = "El mesa_origen_id es obligatorio para fusionar")
    @JsonProperty("mesa_origen_id")
    private Integer mesaOrigenId;

    public FusionarMesasRequest() {
    }

    public FusionarMesasRequest(Integer mesaOrigenId) {
        this.mesaOrigenId = mesaOrigenId;
    }

    public Integer getMesaOrigenId() {
        return mesaOrigenId;
    }

    public void setMesaOrigenId(Integer mesaOrigenId) {
        this.mesaOrigenId = mesaOrigenId;
    }
}
