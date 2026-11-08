package com.lacasablanca.backend.repository;

import com.lacasablanca.backend.model.SesionCaja;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SesionCajaRepository extends JpaRepository<SesionCaja, Integer> {

    Optional<SesionCaja> findFirstByEstadoOrderByFechaAperturaDesc(String estado);
}
