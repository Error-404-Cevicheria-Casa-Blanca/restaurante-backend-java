package com.lacasablanca.backend.dto;

public class UsuarioDto {

    private Integer id;
    private String nombre;
    private String rol;

    public UsuarioDto() {}

    public UsuarioDto(Integer id, String nombre, String rol) {
        this.id = id;
        this.nombre = nombre;
        this.rol = rol;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }
}
