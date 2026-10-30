package com.lacasablanca.backend.repository;

import com.lacasablanca.backend.model.DetallePedido;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DetallePedidoRepository extends JpaRepository<DetallePedido, Integer> {
}
