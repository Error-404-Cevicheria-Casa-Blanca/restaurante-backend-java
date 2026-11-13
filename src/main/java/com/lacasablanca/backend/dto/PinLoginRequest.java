package com.lacasablanca.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class PinLoginRequest {

    @NotBlank(message = "El usuario es obligatorio")
    private String usuario;

    @NotBlank(message = "El PIN es obligatorio")
    @Pattern(regexp = "^\\d{4}$", message = "El PIN debe contener exactamente 4 dígitos numéricos")
    private String pin;

    @JsonProperty("terminal_codigo")
    private String terminalCodigo;

    public PinLoginRequest() {
    }

    public PinLoginRequest(String usuario, String pin) {
        this.usuario = usuario;
        this.pin = pin;
    }

    public PinLoginRequest(String usuario, String pin, String terminalCodigo) {
        this.usuario = usuario;
        this.pin = pin;
        this.terminalCodigo = terminalCodigo;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }

    public String getTerminalCodigo() {
        return terminalCodigo;
    }

    public void setTerminalCodigo(String terminalCodigo) {
        this.terminalCodigo = terminalCodigo;
    }
}
