package com.lacasablanca.backend.repository;

import com.lacasablanca.backend.model.EstadoMesa;
import com.lacasablanca.backend.model.Mesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MesaRepository extends JpaRepository<Mesa, Integer> {
    List<Mesa> findAllByOrderByNumeroAsc();

    @Modifying
    @Query("UPDATE Mesa m SET m.estado = :nuevoEstado WHERE m.id = :id AND m.estado = :estadoEsperado")
    int actualizarEstadoCondicional(@Param("id") Integer id,
                                   @Param("nuevoEstado") EstadoMesa nuevoEstado,
                                   @Param("estadoEsperado") EstadoMesa estadoEsperado);

    @Query("SELECT m.estado FROM Mesa m WHERE m.id = :id")
    Optional<EstadoMesa> findEstadoById(@Param("id") Integer id);
}
