package com.lacasablanca.backend.dto;

import java.math.BigDecimal;

public class PedidoResumenCuentaDto {

    private Integer id;
    private String estado;
    private BigDecimal total;

    public PedidoResumenCuentaDto() {
    }

    public PedidoResumenCuentaDto(Integer id, String estado, BigDecimal total) {
        this.id = id;
        this.estado = estado;
        this.total = total;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }
}
