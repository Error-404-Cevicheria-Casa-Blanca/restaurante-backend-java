package com.lacasablanca.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class UsuarioDto {

    private Integer id;
    private String nombre;

    @JsonProperty("nombre_visible")
    private String nombreVisible;

    private String rol;

    @JsonProperty("turno")
    private TurnoDto turno;

    @JsonProperty("terminal")
    private TerminalDto terminal;

    public UsuarioDto() {
    }

    public UsuarioDto(Integer id, String nombre, String rol) {
        this.id = id;
        this.nombre = nombre;
        this.rol = rol;
    }

    public UsuarioDto(Integer id, String nombre, String nombreVisible, String rol,
                      TurnoDto turno, TerminalDto terminal) {
        this.id = id;
        this.nombre = nombre;
        this.nombreVisible = nombreVisible;
        this.rol = rol;
        this.turno = turno;
        this.terminal = terminal;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getNombreVisible() {
        return nombreVisible;
    }

    public void setNombreVisible(String nombreVisible) {
        this.nombreVisible = nombreVisible;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public TurnoDto getTurno() {
        return turno;
    }

    public void setTurno(TurnoDto turno) {
        this.turno = turno;
    }

    public TerminalDto getTerminal() {
        return terminal;
    }

    public void setTerminal(TerminalDto terminal) {
        this.terminal = terminal;
    }
}
