package com.lacasablanca.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lacasablanca.backend.model.Turno;

public class TurnoDto {

    private Integer id;
    private String nombre;
    private String inicio;
    private String fin;

    public TurnoDto() {
    }

    public TurnoDto(Integer id, String nombre, String inicio, String fin) {
        this.id = id;
        this.nombre = nombre;
        this.inicio = inicio;
        this.fin = fin;
    }

    public static TurnoDto desdeEntidad(Turno turno) {
        if (turno == null) {
            return null;
        }
        return new TurnoDto(
                turno.getId(),
                turno.getNombre(),
                turno.getHoraInicio() != null ? turno.getHoraInicio().toString() : null,
                turno.getHoraFin() != null ? turno.getHoraFin().toString() : null
        );
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

    public String getInicio() {
        return inicio;
    }

    public void setInicio(String inicio) {
        this.inicio = inicio;
    }

    public String getFin() {
        return fin;
    }

    public void setFin(String fin) {
        this.fin = fin;
    }
}
