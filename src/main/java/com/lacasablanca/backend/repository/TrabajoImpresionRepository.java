package com.lacasablanca.backend.repository;

import com.lacasablanca.backend.model.TrabajoImpresion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TrabajoImpresionRepository extends JpaRepository<TrabajoImpresion, Integer> {
    List<TrabajoImpresion> findByPedidoIdOrderByFechaCreacionDesc(Integer pedidoId);
}
