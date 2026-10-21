package com.lacasablanca.backend.dto;

public class LoginResponse {

    private String token;
    private String tipo = "Bearer";
    private UsuarioDto usuario;

    public LoginResponse() {}

    public LoginResponse(String token, UsuarioDto usuario) {
        this.token = token;
        this.tipo = "Bearer";
        this.usuario = usuario;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public UsuarioDto getUsuario() { return usuario; }
    public void setUsuario(UsuarioDto usuario) { this.usuario = usuario; }
}
