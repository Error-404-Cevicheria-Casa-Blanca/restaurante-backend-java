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
    @DisplayName("PRB-20 Rollback IT: Fallo en el 2.º detalle hace rollback completo sin dejar pedido ni mesa OCUPADA")
    void falloEnSegundoDetalleNoDejaPedidoNiMesaOcupada() {
        // Aseguramos mesa 7 en LIBRE
        Mesa mesa = mesaRepository.findById(7).orElseThrow();
        mesa.setEstado(EstadoMesa.LIBRE);
        mesaRepository.save(mesa);

        long pedidosAntes = pedidoRepository.count();

        // El segundo item tiene cantidad = 0 que viola la validación del servicio
        CrearPedidoRequest req = new CrearPedidoRequest(7, List.of(
                new ItemPedidoRequest(1, 1, "ok"),
                new ItemPedidoRequest(2, 0, "falla por cantidad cero")
        ));

        assertThrows(IllegalArgumentException.class, () -> pedidoService.registrarPedido(req, "2"));

        // Verificamos que la mesa NO quedó en OCUPADA (rollback exitoso)
        Mesa mesaDespues = mesaRepository.findById(7).orElseThrow();
        assertEquals(EstadoMesa.LIBRE, mesaDespues.getEstado());

        // Verificamos que NO se persistió ningún pedido
        assertEquals(pedidosAntes, pedidoRepository.count());
    }
}
