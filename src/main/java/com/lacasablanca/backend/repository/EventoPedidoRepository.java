package com.lacasablanca.backend.repository;

import com.lacasablanca.backend.model.EventoPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EventoPedidoRepository extends JpaRepository<EventoPedido, Integer> {
    List<EventoPedido> findByPedidoIdOrderByFechaAsc(Integer pedidoId);
}
