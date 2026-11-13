package com.lacasablanca.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class LoginResponse {

    private String token;
    private String tipo = "Bearer";
    private UsuarioDto usuario;

    @JsonProperty("fecha_hora_servidor")
    private String fechaHoraServidor;

    public LoginResponse() {
    }

    public LoginResponse(String token, UsuarioDto usuario) {
        this.token = token;
        this.tipo = "Bearer";
        this.usuario = usuario;
    }

    public LoginResponse(String token, UsuarioDto usuario, String fechaHoraServidor) {
        this.token = token;
        this.tipo = "Bearer";
        this.usuario = usuario;
        this.fechaHoraServidor = fechaHoraServidor;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public UsuarioDto getUsuario() {
        return usuario;
    }

    public void setUsuario(UsuarioDto usuario) {
        this.usuario = usuario;
    }

    public String getFechaHoraServidor() {
        return fechaHoraServidor;
    }

    public void setFechaHoraServidor(String fechaHoraServidor) {
        this.fechaHoraServidor = fechaHoraServidor;
    }
}
