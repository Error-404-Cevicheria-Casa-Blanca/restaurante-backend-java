package com.lacasablanca.backend.repository;

import com.lacasablanca.backend.model.Terminal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TerminalRepository extends JpaRepository<Terminal, Integer> {

    Optional<Terminal> findByCodigo(String codigo);

    Optional<Terminal> findByCodigoAndActivoTrue(String codigo);
}
