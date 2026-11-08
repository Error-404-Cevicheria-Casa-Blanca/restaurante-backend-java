package com.lacasablanca.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class DividirMesaRequest {

    @NotEmpty(message = "Debe indicar al menos un item_id para dividir la cuenta")
    @JsonProperty("item_ids")
    private List<Integer> itemIds;

    @JsonProperty("mesa_destino_id")
    private Integer mesaDestinoId;

    public DividirMesaRequest() {
    }

    public DividirMesaRequest(List<Integer> itemIds, Integer mesaDestinoId) {
        this.itemIds = itemIds;
        this.mesaDestinoId = mesaDestinoId;
    }

    public List<Integer> getItemIds() {
        return itemIds;
    }

    public void setItemIds(List<Integer> itemIds) {
        this.itemIds = itemIds;
    }

    public Integer getMesaDestinoId() {
        return mesaDestinoId;
    }

    public void setMesaDestinoId(Integer mesaDestinoId) {
        this.mesaDestinoId = mesaDestinoId;
    }
}
