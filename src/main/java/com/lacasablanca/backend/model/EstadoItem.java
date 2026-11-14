package com.lacasablanca.backend.model;

import java.util.List;
import java.util.Map;

/**
 * Estado de preparación de una línea (plato) de un pedido (PRB-87).
 * Conjunto canónico compartido entre BD (db/schema.sql), Django KDS (catalogo/kds_service.py)
 * y Backend Java: PENDIENTE, COCINANDO, EN_PREPARACION, LISTO, SERVIDO, ANULADO.
 */
public enum EstadoItem {
    PENDIENTE,
    COCINANDO,
    EN_PREPARACION,
    LISTO,
    SERVIDO,
    ANULADO;

    private static final Map<EstadoItem, List<EstadoItem>> TRANSICIONES = Map.of(
            PENDIENTE, List.of(COCINANDO, EN_PREPARACION, ANULADO),
            COCINANDO, List.of(EN_PREPARACION, LISTO, ANULADO),
            EN_PREPARACION, List.of(LISTO, ANULADO),
            LISTO, List.of(SERVIDO, ANULADO),
            SERVIDO, List.of(),
            ANULADO, List.of()
    );

    /** Estados destino permitidos desde el estado actual. */
    public List<EstadoItem> siguientesPermitidos() {
        return TRANSICIONES.getOrDefault(this, List.of());
    }

    /** Indica si pasar de este estado a {@code destino} es una transición permitida. */
    public boolean puedePasarA(EstadoItem destino) {
        return destino != null && siguientesPermitidos().contains(destino);
    }
}
