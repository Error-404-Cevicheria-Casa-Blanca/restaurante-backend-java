package com.lacasablanca.backend.repository;

import com.lacasablanca.backend.model.EstadoPedido;
import com.lacasablanca.backend.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Integer> {
    java.util.Optional<Pedido> findByClientRequestId(String clientRequestId);

    List<Pedido> findByMesaIdOrderByFechaCreacionDesc(Integer mesaId);

    List<Pedido> findByMesaIdAndEstadoNotOrderByFechaCreacionAsc(Integer mesaId, EstadoPedido estado);

    List<Pedido> findByMesaIdAndEstadoNotInOrderByFechaCreacionAsc(
            Integer mesaId, Collection<EstadoPedido> estadosExcluidos
    );
}
