package com.lacasablanca.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

    @NotBlank(message = "El usuario es obligatorio")
    @JsonProperty("usuario")
    private String usuario;

    @NotBlank(message = "La clave es obligatoria")
    @JsonProperty("clave")
    private String clave;

    @JsonProperty("terminal_codigo")
    private String terminalCodigo;

    public LoginRequest() {
    }

    public LoginRequest(String usuario, String clave) {
        this.usuario = usuario;
        this.clave = clave;
    }

    public LoginRequest(String usuario, String clave, String terminalCodigo) {
        this.usuario = usuario;
        this.clave = clave;
        this.terminalCodigo = terminalCodigo;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getClave() {
        return clave;
    }

    public void setClave(String clave) {
        this.clave = clave;
    }

    public String getTerminalCodigo() {
        return terminalCodigo;
    }

    public void setTerminalCodigo(String terminalCodigo) {
        this.terminalCodigo = terminalCodigo;
    }
}
