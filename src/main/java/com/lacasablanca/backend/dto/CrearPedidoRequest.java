package com.lacasablanca.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class CrearPedidoRequest {

    @JsonProperty("mesa_id")
    private Integer mesaId;

    @NotEmpty(message = "La lista de items no puede estar vacía")
    @Valid
    private List<ItemPedidoRequest> items;

    @JsonProperty("client_request_id")
    private String clientRequestId;

    @JsonProperty("tipo_pedido")
    private String tipoPedido;

    @JsonProperty("cliente_nombre")
    private String clienteNombre;

    public CrearPedidoRequest() {
    }

    public CrearPedidoRequest(Integer mesaId, List<ItemPedidoRequest> items) {
        this.mesaId = mesaId;
        this.items = items;
    }

    public CrearPedidoRequest(Integer mesaId, List<ItemPedidoRequest> items, String clientRequestId) {
        this.mesaId = mesaId;
        this.items = items;
        this.clientRequestId = clientRequestId;
    }

    public CrearPedidoRequest(Integer mesaId,
                              List<ItemPedidoRequest> items,
                              String clientRequestId,
                              String tipoPedido,
                              String clienteNombre) {
        this.mesaId = mesaId;
        this.items = items;
        this.clientRequestId = clientRequestId;
        this.tipoPedido = tipoPedido;
        this.clienteNombre = clienteNombre;
    }

    public Integer getMesaId() {
        return mesaId;
    }

    public void setMesaId(Integer mesaId) {
        this.mesaId = mesaId;
    }

    public List<ItemPedidoRequest> getItems() {
        return items;
    }

    public void setItems(List<ItemPedidoRequest> items) {
        this.items = items;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }

    public String getTipoPedido() {
        return tipoPedido;
    }

    public void setTipoPedido(String tipoPedido) {
        this.tipoPedido = tipoPedido;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public void setClienteNombre(String clienteNombre) {
        this.clienteNombre = clienteNombre;
    }
}
