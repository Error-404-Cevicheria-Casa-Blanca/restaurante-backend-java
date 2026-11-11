package com.lacasablanca.backend.dto;

import com.lacasablanca.backend.model.Terminal;

public class TerminalDto {

    private Integer id;
    private String codigo;
    private String nombre;

    public TerminalDto() {
    }

    public TerminalDto(Integer id, String codigo, String nombre) {
        this.id = id;
        this.codigo = codigo;
        this.nombre = nombre;
    }

    public static TerminalDto desdeEntidad(Terminal terminal) {
        if (terminal == null) {
            return null;
        }
        return new TerminalDto(
                terminal.getId(),
                terminal.getCodigo(),
                terminal.getNombre()
        );
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }
}
