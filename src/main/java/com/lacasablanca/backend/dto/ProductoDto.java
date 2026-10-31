package com.lacasablanca.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lacasablanca.backend.model.Estacion;
import java.math.BigDecimal;

public class ProductoDto {
    private Integer id;
    private String nombre;
    private String descripcion;
    private BigDecimal precio;
    private Boolean disponible;

    @JsonProperty("categoria_id")
    private Integer categoriaId;

    @JsonProperty("imagen_url")
    private String imagenUrl;

    private Estacion estacion;

    public ProductoDto() {}

    public ProductoDto(Integer id, String nombre, String descripcion, BigDecimal precio, Boolean disponible, Integer categoriaId, String imagenUrl, Estacion estacion) {
        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.precio = precio;
        this.disponible = disponible;
        this.categoriaId = categoriaId;
        this.imagenUrl = imagenUrl;
        this.estacion = estacion;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }

    public Boolean getDisponible() { return disponible; }
    public void setDisponible(Boolean disponible) { this.disponible = disponible; }

    public Integer getCategoriaId() { return categoriaId; }
    public void setCategoriaId(Integer categoriaId) { this.categoriaId = categoriaId; }

    public String getImagenUrl() { return imagenUrl; }
    public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }

    public Estacion getEstacion() { return estacion; }
    public void setEstacion(Estacion estacion) { this.estacion = estacion; }
}
