package com.lacasablanca.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "token_revocado")
public class TokenRevocado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(name = "fecha_revocacion", nullable = false)
    private OffsetDateTime fechaRevocacion = OffsetDateTime.now();

    public TokenRevocado() {}

    public TokenRevocado(String tokenHash) {
        this.tokenHash = tokenHash;
        this.fechaRevocacion = OffsetDateTime.now();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public OffsetDateTime getFechaRevocacion() {
        return fechaRevocacion;
    }

    public void setFechaRevocacion(OffsetDateTime fechaRevocacion) {
        this.fechaRevocacion = fechaRevocacion;
    }
}
