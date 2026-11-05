package com.lacasablanca.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public class CobrarRequest {

    @NotNull(message = "La lista de pagos es obligatoria")
    @NotEmpty(message = "Debe registrar al menos un pago")
    @Valid
    private List<PagoRequestDto> pagos;

    private BigDecimal propina = BigDecimal.ZERO;

    private String observacion;

    @NotBlank(message = "El client_request_id es obligatorio para idempotencia")
    @JsonProperty("client_request_id")
    private String clientRequestId;

    public CobrarRequest() {
    }

    public CobrarRequest(List<PagoRequestDto> pagos, BigDecimal propina, String observacion, String clientRequestId) {
        this.pagos = pagos;
        this.propina = (propina != null) ? propina : BigDecimal.ZERO;
        this.observacion = observacion;
        this.clientRequestId = clientRequestId;
    }

    public List<PagoRequestDto> getPagos() {
        return pagos;
    }

    public void setPagos(List<PagoRequestDto> pagos) {
        this.pagos = pagos;
    }

    public BigDecimal getPropina() {
        return propina;
    }

    public void setPropina(BigDecimal propina) {
        this.propina = (propina != null) ? propina : BigDecimal.ZERO;
    }

    public String getObservacion() {
        return observacion;
    }

    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }
}
