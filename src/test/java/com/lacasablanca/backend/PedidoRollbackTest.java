package com.lacasablanca.backend;

import com.lacasablanca.backend.dto.CrearPedidoRequest;
import com.lacasablanca.backend.dto.ItemPedidoRequest;
import com.lacasablanca.backend.model.EstadoMesa;
import com.lacasablanca.backend.model.Mesa;
import com.lacasablanca.backend.repository.MesaRepository;
import com.lacasablanca.backend.repository.PedidoRepository;
import com.lacasablanca.backend.service.PedidoService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PedidoRollbackTest {

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private MesaRepository mesaRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    @Test
    @DisplayName("PRB-20 E3: fallo real al insertar el 2.º detalle revierte la mesa y no deja pedido")
    void falloEnInsercionDeDetalleHaceRollbackCompleto() {
        // Mesa 7 en LIBRE
        Mesa mesa = mesaRepository.findById(7).orElseThrow();
        mesa.setEstado(EstadoMesa.LIBRE);
        mesaRepository.save(mesa);

        long pedidosAntes = pedidoRepository.count();

        // `notas` excede varchar(255) => DataIntegrityViolationException al insertar el detalle
        String notasDemasiadoLargas = "x".repeat(300);

        CrearPedidoRequest req = new CrearPedidoRequest(7, List.of(
                new ItemPedidoRequest(1, 1, "ok"),
                new ItemPedidoRequest(2, 1, notasDemasiadoLargas)
        ));

        assertThrows(DataIntegrityViolationException.class,
                () -> pedidoService.registrarPedido(req, "2"));

        // El rollback debe devolver la mesa a LIBRE
        assertEquals(EstadoMesa.LIBRE,
                mesaRepository.findById(7).orElseThrow().getEstado(),
                "La mesa debió revertir a LIBRE por el rollback");

        // No debe quedar ningún pedido persistido
        assertEquals(pedidosAntes, pedidoRepository.count(),
                "No debe quedar ningún pedido persistido");
    }
}
