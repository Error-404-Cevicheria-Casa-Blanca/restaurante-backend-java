package com.lacasablanca.backend.repository;

import com.lacasablanca.backend.model.SesionMozo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SesionMozoRepository extends JpaRepository<SesionMozo, Integer> {

    Optional<SesionMozo> findFirstByUsuarioIdAndFinIsNullOrderByInicioDesc(Integer usuarioId);

    List<SesionMozo> findByUsuarioIdAndFinIsNull(Integer usuarioId);

    List<SesionMozo> findByTerminalIdAndFinIsNull(Integer terminalId);
}
