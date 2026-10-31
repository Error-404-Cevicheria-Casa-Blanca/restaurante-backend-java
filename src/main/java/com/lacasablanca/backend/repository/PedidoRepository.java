package com.lacasablanca.backend.repository;

import com.lacasablanca.backend.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PedidoRepository extends JpaRepository<Pedido, Integer> {
    List<Pedido> findByMesaIdOrderByFechaCreacionDesc(Integer mesaId);
}
