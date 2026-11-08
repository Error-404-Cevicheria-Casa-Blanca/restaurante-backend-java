package com.lacasablanca.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public class CuentaResponse {

    @JsonProperty("mesa_id")
    private Integer mesaId;

    private List<PedidoResumenCuentaDto> pedidos;
    private List<ItemCuentaDto> items;
    private BigDecimal subtotal;
    private BigDecimal igv;
    private BigDecimal total;

    @JsonProperty("propina_sugerida")
    private BigDecimal propinaSugerida;

    @JsonProperty("impresion_job_id")
    private String impresionJobId;

    @JsonProperty("estado_impresion")
    private String estadoImpresion;

    @JsonProperty("intentos_impresion")
    private Integer intentosImpresion;

    public CuentaResponse() {
    }

    public CuentaResponse(Integer mesaId, List<PedidoResumenCuentaDto> pedidos,
                          List<ItemCuentaDto> items, BigDecimal subtotal,
                          BigDecimal igv, BigDecimal total, BigDecimal propinaSugerida) {
        this.mesaId = mesaId;
        this.pedidos = pedidos;
        this.items = items;
        this.subtotal = subtotal;
        this.igv = igv;
        this.total = total;
        this.propinaSugerida = propinaSugerida;
    }

    public Integer getMesaId() {
        return mesaId;
    }

    public void setMesaId(Integer mesaId) {
        this.mesaId = mesaId;
    }

    public List<PedidoResumenCuentaDto> getPedidos() {
        return pedidos;
    }

    public void setPedidos(List<PedidoResumenCuentaDto> pedidos) {
        this.pedidos = pedidos;
    }

    public List<ItemCuentaDto> getItems() {
        return items;
    }

    public void setItems(List<ItemCuentaDto> items) {
        this.items = items;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getIgv() {
        return igv;
    }

    public void setIgv(BigDecimal igv) {
        this.igv = igv;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public BigDecimal getPropinaSugerida() {
        return propinaSugerida;
    }

    public void setPropinaSugerida(BigDecimal propinaSugerida) {
        this.propinaSugerida = propinaSugerida;
    }

    public String getImpresionJobId() {
        return impresionJobId;
    }

    public void setImpresionJobId(String impresionJobId) {
        this.impresionJobId = impresionJobId;
    }

    public String getEstadoImpresion() {
        return estadoImpresion;
    }

    public void setEstadoImpresion(String estadoImpresion) {
        this.estadoImpresion = estadoImpresion;
    }

    public Integer getIntentosImpresion() {
        return intentosImpresion;
    }

    public void setIntentosImpresion(Integer intentosImpresion) {
        this.intentosImpresion = intentosImpresion;
    }
}
