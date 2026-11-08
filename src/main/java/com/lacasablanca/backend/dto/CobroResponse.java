package com.lacasablanca.backend.dto;

import java.math.BigDecimal;
import java.util.List;

public class CobroResponse {

    private BigDecimal total;
    private BigDecimal pagado;
    private BigDecimal vuelto;
    private List<PedidoResumenCuentaDto> pedidos;

    public CobroResponse() {
    }

    public CobroResponse(BigDecimal total, BigDecimal pagado, BigDecimal vuelto, List<PedidoResumenCuentaDto> pedidos) {
        this.total = total;
        this.pagado = pagado;
        this.vuelto = vuelto;
        this.pedidos = pedidos;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public BigDecimal getPagado() {
        return pagado;
    }

    public void setPagado(BigDecimal pagado) {
        this.pagado = pagado;
    }

    public BigDecimal getVuelto() {
        return vuelto;
    }

    public void setVuelto(BigDecimal vuelto) {
        this.vuelto = vuelto;
    }

    public List<PedidoResumenCuentaDto> getPedidos() {
        return pedidos;
    }

    public void setPedidos(List<PedidoResumenCuentaDto> pedidos) {
        this.pedidos = pedidos;
    }
}
