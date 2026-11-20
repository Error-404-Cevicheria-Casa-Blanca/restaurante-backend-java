package com.lacasablanca.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.OffsetDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Entity
@Table(name = "impresion_cola")
public class TrabajoImpresion {

    private static final Pattern PATRON_MESA_ID = Pattern.compile(
            "(?:\"mesa_id\"\\s*:\\s*|\\bmesa_id\\s*[:=]\\s*|\\bmesa\\s+#?\\s*)(\\d+)\\b",
            Pattern.CASE_INSENSITIVE
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    @Transient
    private Integer mesaId;

    @Column(name = "tipo", nullable = false, length = 20)
    private String tipo;

    @Column(name = "estacion", length = 30)
    private String estacion;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = "PENDIENTE";

    @Column(name = "intentos", nullable = false)
    private Integer intentos = 0;

    @Column(name = "error", columnDefinition = "TEXT")
    private String error;

    @Column(name = "fecha_creacion", nullable = false)
    private OffsetDateTime fechaCreacion = OffsetDateTime.now();

    @Column(name = "fecha_impresion")
    private OffsetDateTime fechaImpresion;

    public TrabajoImpresion() {}

    public TrabajoImpresion(Pedido pedido, String tipo, String estacion, String payload, String estado) {
        this(pedido, (pedido != null && pedido.getMesa() != null) ? pedido.getMesa().getId() : null,
                tipo, estacion, payload, estado);
    }

    public TrabajoImpresion(Pedido pedido, Integer mesaId, String tipo, String estacion, String payload, String estado) {
        this.pedido = pedido;
        Integer resolvedMesaId = mesaId != null
                ? mesaId
                : ((pedido != null && pedido.getMesa() != null) ? pedido.getMesa().getId() : null);
        this.mesaId = resolvedMesaId;
        this.tipo = tipo;
        this.estacion = estacion;
        if (payload != null && !payload.isEmpty()) {
            this.payload = payload;
        } else if ("PRECUENTA".equals(tipo) && resolvedMesaId != null) {
            this.payload = "{\"mesa_id\":" + resolvedMesaId + "}";
        } else {
            this.payload = "";
        }
        this.estado = estado != null ? estado : "PENDIENTE";
        this.fechaCreacion = OffsetDateTime.now();
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

    public Integer getMesaId() {
        if (mesaId != null) {
            return mesaId;
        }
        if (pedido != null && pedido.getMesa() != null) {
            return pedido.getMesa().getId();
        }
        if (payload != null && !payload.isBlank()) {
            Matcher m = PATRON_MESA_ID.matcher(payload);
            if (m.find()) {
                try {
                    return Integer.valueOf(m.group(1));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    public void setMesaId(Integer mesaId) {
        this.mesaId = mesaId;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getEstacion() {
        return estacion;
    }

    public void setEstacion(String estacion) {
        this.estacion = estacion;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Integer getIntentos() {
        return intentos != null ? intentos : 0;
    }

    public void setIntentos(Integer intentos) {
        this.intentos = intentos;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public OffsetDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(OffsetDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public OffsetDateTime getFechaImpresion() {
        return fechaImpresion;
    }

    public void setFechaImpresion(OffsetDateTime fechaImpresion) {
        this.fechaImpresion = fechaImpresion;
    }
}
