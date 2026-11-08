package com.lacasablanca.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

public class TrabajoImpresionDto {

    @JsonProperty("job_id")
    private String jobId;

    @JsonProperty("mesa_id")
    private Integer mesaId;

    private String tipo;
    private String estado;
    private Integer intentos;

    @JsonProperty("mensaje_error")
    private String mensajeError;

    @JsonProperty("fecha_solicitud")
    private OffsetDateTime fechaSolicitud;

    public TrabajoImpresionDto() {
    }

    public TrabajoImpresionDto(String jobId, Integer mesaId, String tipo,
                               String estado, Integer intentos,
                               String mensajeError, OffsetDateTime fechaSolicitud) {
        this.jobId = jobId;
        this.mesaId = mesaId;
        this.tipo = tipo;
        this.estado = estado;
        this.intentos = intentos;
        this.mensajeError = mensajeError;
        this.fechaSolicitud = fechaSolicitud;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public Integer getMesaId() {
        return mesaId;
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

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Integer getIntentos() {
        return intentos;
    }

    public void setIntentos(Integer intentos) {
        this.intentos = intentos;
    }

    public String getMensajeError() {
        return mensajeError;
    }

    public void setMensajeError(String mensajeError) {
        this.mensajeError = mensajeError;
    }

    public OffsetDateTime getFechaSolicitud() {
        return fechaSolicitud;
    }

    public void setFechaSolicitud(OffsetDateTime fechaSolicitud) {
        this.fechaSolicitud = fechaSolicitud;
    }
}
