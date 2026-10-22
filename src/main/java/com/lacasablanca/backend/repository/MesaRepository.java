package com.lacasablanca.backend.repository;

import com.lacasablanca.backend.model.Mesa;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MesaRepository extends JpaRepository<Mesa, Integer> {
    List<Mesa> findAllByOrderByNumeroAsc();
}
