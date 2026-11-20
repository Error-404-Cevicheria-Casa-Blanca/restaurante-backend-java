package com.lacasablanca.backend.repository;

import com.lacasablanca.backend.model.TrabajoImpresion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TrabajoImpresionRepository extends JpaRepository<TrabajoImpresion, Integer> {
    List<TrabajoImpresion> findByPedidoIdOrderByFechaCreacionDesc(Integer pedidoId);

    List<TrabajoImpresion> findByMesaIdAndTipoOrderByFechaCreacionDesc(Integer mesaId, String tipo);

    List<TrabajoImpresion> findByMesaIdOrderByFechaCreacionDesc(Integer mesaId);

    Optional<TrabajoImpresion> findByPedidoIdAndTipoAndEstacion(Integer pedidoId, String tipo, String estacion);
}
