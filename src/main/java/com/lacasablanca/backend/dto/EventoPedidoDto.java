package com.lacasablanca.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lacasablanca.backend.model.EstadoPedido;
import java.time.LocalDateTime;

public class EventoPedidoDto {

    @JsonProperty("estado_anterior")
    private EstadoPedido estadoAnterior;

    @JsonProperty("estado_nuevo")
    private EstadoPedido estadoNuevo;

    private String usuario;
    private String motivo;
    private LocalDateTime fecha;

    public EventoPedidoDto() {
    }

    public EventoPedidoDto(EstadoPedido estadoAnterior, EstadoPedido estadoNuevo,
                           String usuario, String motivo, LocalDateTime fecha) {
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.usuario = usuario;
        this.motivo = motivo;
        this.fecha = fecha;
    }

    public EstadoPedido getEstadoAnterior() {
        return estadoAnterior;
    }

    public void setEstadoAnterior(EstadoPedido estadoAnterior) {
        this.estadoAnterior = estadoAnterior;
    }

    public EstadoPedido getEstadoNuevo() {
        return estadoNuevo;
    }

    public void setEstadoNuevo(EstadoPedido estadoNuevo) {
        this.estadoNuevo = estadoNuevo;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }
}
