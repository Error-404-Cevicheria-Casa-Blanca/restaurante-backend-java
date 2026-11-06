package com.lacasablanca.backend.repository;

import com.lacasablanca.backend.model.TokenRevocado;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TokenRevocadoRepository extends JpaRepository<TokenRevocado, Integer> {
    Optional<TokenRevocado> findByTokenHash(String tokenHash);
    boolean existsByTokenHash(String tokenHash);
}
