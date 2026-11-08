package com.lacasablanca.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "evento_pedido")
public class EventoPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", length = 20)
    private EstadoPedido estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, length = 20)
    private EstadoPedido estadoNuevo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "motivo", length = 255)
    private String motivo;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    public EventoPedido() {}

    public EventoPedido(Pedido pedido,
                        EstadoPedido estadoAnterior,
                        EstadoPedido estadoNuevo,
                        Usuario usuario,
                        String motivo) {
        this.pedido = pedido;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.usuario = usuario;
        this.motivo = motivo;
        this.fecha = LocalDateTime.now();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public void setPedido(Pedido pedido) {
        this.pedido = pedido;
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

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
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
