package com.lacasablanca.backend.repository;

import com.lacasablanca.backend.model.Turno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TurnoRepository extends JpaRepository<Turno, Integer> {

    Optional<Turno> findByNombre(String nombre);

    List<Turno> findByActivoTrue();
}
