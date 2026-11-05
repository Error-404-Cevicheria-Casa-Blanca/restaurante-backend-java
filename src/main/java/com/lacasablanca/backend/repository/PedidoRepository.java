package com.lacasablanca.backend.repository;

import com.lacasablanca.backend.model.EstadoPedido;
import com.lacasablanca.backend.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Integer> {
    List<Pedido> findByMesaIdOrderByFechaCreacionDesc(Integer mesaId);

    List<Pedido> findByMesaIdAndEstadoNotOrderByFechaCreacionAsc(Integer mesaId, EstadoPedido estado);
}
