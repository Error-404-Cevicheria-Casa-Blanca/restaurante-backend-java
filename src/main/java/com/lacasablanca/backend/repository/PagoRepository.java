package com.lacasablanca.backend.repository;

import com.lacasablanca.backend.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Integer> {

    Optional<Pago> findByClientRequestId(String clientRequestId);

    List<Pago> findByMesaIdOrderByFechaPagoAsc(Integer mesaId);

    List<Pago> findByPedidoIdOrderByFechaPagoAsc(Integer pedidoId);
}
