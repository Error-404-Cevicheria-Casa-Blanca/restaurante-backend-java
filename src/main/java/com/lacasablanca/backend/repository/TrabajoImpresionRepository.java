package com.lacasablanca.backend.repository;

import com.lacasablanca.backend.model.TrabajoImpresion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TrabajoImpresionRepository extends JpaRepository<TrabajoImpresion, Integer> {
    List<TrabajoImpresion> findByPedidoIdOrderByFechaCreacionDesc(Integer pedidoId);

    List<TrabajoImpresion> findByTipoOrderByFechaCreacionDesc(String tipo);

    List<TrabajoImpresion> findByPedidoIdAndTipoAndEstacionOrderByFechaCreacionDescIdDesc(
            Integer pedidoId, String tipo, String estacion
    );

    List<TrabajoImpresion> findByTipoAndPedidoMesaIdOrderByFechaCreacionDescIdDesc(
            String tipo, Integer mesaId
    );

    List<TrabajoImpresion> findByTipoAndPayloadContainingOrderByFechaCreacionDescIdDesc(
            String tipo, String payloadFragment
    );
}
