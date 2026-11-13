package com.lacasablanca.backend.model;

/**
 * Estado de preparación de una línea (plato) de un pedido (PRB-87).
 * Flujo único permitido: PENDIENTE → EN_PREPARACION → LISTO → SERVIDO.
 */
public enum EstadoItem {
    PENDIENTE,
    EN_PREPARACION,
    LISTO,
    SERVIDO;

    /** Estado inmediatamente posterior en el flujo, o {@code null} si es el último. */
    public EstadoItem siguiente() {
        EstadoItem[] valores = values();
        return ordinal() + 1 < valores.length ? valores[ordinal() + 1] : null;
    }

    /** Indica si pasar de este estado a {@code destino} es una transición permitida. */
    public boolean puedePasarA(EstadoItem destino) {
        return destino != null && destino == siguiente();
    }
}
