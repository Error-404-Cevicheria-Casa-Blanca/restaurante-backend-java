package com.lacasablanca.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lacasablanca.backend.model.EstadoPedido;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class PedidoResponse {

    private Integer id;

    @JsonProperty("mesa_id")
    private Integer mesaId;

    @JsonProperty("mozo_id")
    private Integer mozoId;

    @JsonProperty("mozo_nombre")
    private String mozoNombre;

    private EstadoPedido estado;
    private BigDecimal total;

    @JsonProperty("fecha_creacion")
    private LocalDateTime fechaCreacion;

    private List<DetallePedidoResponse> items;

    public PedidoResponse() {}

    public PedidoResponse(Integer id, Integer mesaId, Integer mozoId, String mozoNombre, EstadoPedido estado, BigDecimal total, LocalDateTime fechaCreacion, List<DetallePedidoResponse> items) {
        this.id = id;
        this.mesaId = mesaId;
        this.mozoId = mozoId;
        this.mozoNombre = mozoNombre;
        this.estado = estado;
        this.total = total;
        this.fechaCreacion = fechaCreacion;
        this.items = items;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getMesaId() { return mesaId; }
    public void setMesaId(Integer mesaId) { this.mesaId = mesaId; }

    public Integer getMozoId() { return mozoId; }
    public void setMozoId(Integer mozoId) { this.mozoId = mozoId; }

    public String getMozoNombre() { return mozoNombre; }
    public void setMozoNombre(String mozoNombre) { this.mozoNombre = mozoNombre; }

    public EstadoPedido getEstado() { return estado; }
    public void setEstado(EstadoPedido estado) { this.estado = estado; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public List<DetallePedidoResponse> getItems() { return items; }
    public void setItems(List<DetallePedidoResponse> items) { this.items = items; }
}
