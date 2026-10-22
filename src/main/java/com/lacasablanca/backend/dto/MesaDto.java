package com.lacasablanca.backend.dto;

import com.lacasablanca.backend.model.EstadoMesa;

public class MesaDto {

    private Integer id;
    private Integer numero;
    private Integer capacidad;
    private String ubicacion;
    private EstadoMesa estado;

    public MesaDto() {}

    public MesaDto(Integer id, Integer numero, Integer capacidad, String ubicacion, EstadoMesa estado) {
        this.id = id;
        this.numero = numero;
        this.capacidad = capacidad;
        this.ubicacion = ubicacion;
        this.estado = estado;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getNumero() { return numero; }
    public void setNumero(Integer numero) { this.numero = numero; }

    public Integer getCapacidad() { return capacidad; }
    public void setCapacidad(Integer capacidad) { this.capacidad = capacidad; }

    public String getUbicacion() { return ubicacion; }
    public void setUbicacion(String ubicacion) { this.ubicacion = ubicacion; }

    public EstadoMesa getEstado() { return estado; }
    public void setEstado(EstadoMesa estado) { this.estado = estado; }
}
