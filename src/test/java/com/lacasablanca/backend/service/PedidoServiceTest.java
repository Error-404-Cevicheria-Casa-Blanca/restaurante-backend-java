package com.lacasablanca.backend.service;

import com.lacasablanca.backend.dto.CrearPedidoRequest;
import com.lacasablanca.backend.dto.ItemPedidoRequest;
import com.lacasablanca.backend.dto.PedidoResponse;
import com.lacasablanca.backend.exception.ConflictoOperacionException;
import com.lacasablanca.backend.exception.RecursoNoEncontradoException;
import com.lacasablanca.backend.model.*;
import com.lacasablanca.backend.repository.MesaRepository;
import com.lacasablanca.backend.repository.PedidoRepository;
import com.lacasablanca.backend.repository.ProductoRepository;
import com.lacasablanca.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private MesaRepository mesaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ProductoRepository productoRepository;

    @InjectMocks
    private PedidoService pedidoService;

    @Test
    @DisplayName("PRB-20 Caso 2 x 48.00: Calcula total 96.00, subtotal 81.36 e IGV 14.64 (Opción B - D13)")
    void crearPedidoExitosoCalculaTotalSubtotalEIgvD13() {
        Mesa mesa = new Mesa(1, 1, 4, "Primer piso", EstadoMesa.LIBRE);
        Usuario mozo = new Usuario(2, "mozo_01", "hash", new Rol("MOZO"));
        Categoria cat = new Categoria(1, "Platos de Fondo");
        Producto prod1 = new Producto(10, "Ceviche Clásico", "desc", new BigDecimal("48.00"), true, cat, "url");

        when(mesaRepository.findById(1)).thenReturn(Optional.of(mesa));
        when(usuarioRepository.findById(2)).thenReturn(Optional.of(mozo));
        when(productoRepository.findById(10)).thenReturn(Optional.of(prod1));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            p.setId(100);
            return p;
        });

        CrearPedidoRequest req = new CrearPedidoRequest(1, List.of(new ItemPedidoRequest(10, 2, "sin picante")));
        PedidoResponse res = pedidoService.registrarPedido(req, "2");

        assertNotNull(res);
        assertEquals(100, res.getId());
        assertEquals(new BigDecimal("96.00"), res.getTotal());
        assertEquals(new BigDecimal("81.36"), res.getSubtotal());
        assertEquals(new BigDecimal("14.64"), res.getIgv());
        assertEquals(EstadoPedido.PENDIENTE, res.getEstado());
        assertEquals(EstadoMesa.OCUPADA, mesa.getEstado());
        assertNotNull(res.getItemsPorEstacion());
        assertTrue(res.getItemsPorEstacion().containsKey(Estacion.BARRA_FRIA));
        verify(mesaRepository).save(mesa);
    }

    @Test
    @DisplayName("PRB-20 Fallback de Mozo: Subject inexistente lanza RecursoNoEncontradoException sin guardar")
    void mozoInexistenteLanzaNotFoundYNoGuarda() {
        Mesa mesa = new Mesa(1, 1, 4, "Primer piso", EstadoMesa.LIBRE);
        when(mesaRepository.findById(1)).thenReturn(Optional.of(mesa));
        when(usuarioRepository.findById(9999)).thenReturn(Optional.empty());

        CrearPedidoRequest req = new CrearPedidoRequest(1, List.of(new ItemPedidoRequest(10, 1, null)));

        assertThrows(RecursoNoEncontradoException.class, () -> pedidoService.registrarPedido(req, "9999"));
        verifyNoInteractions(pedidoRepository);
    }

    @Test
    @DisplayName("PRB-20 Conflicto Mesa: Mesa ocupada lanza ConflictoOperacionException (409)")
    void crearPedidoMesaOcupadaLanzaConflicto() {
        Mesa mesaOcupada = new Mesa(1, 1, 4, "Primer piso", EstadoMesa.OCUPADA);
        when(mesaRepository.findById(1)).thenReturn(Optional.of(mesaOcupada));

        CrearPedidoRequest req = new CrearPedidoRequest(1, List.of(new ItemPedidoRequest(10, 1, null)));

        assertThrows(ConflictoOperacionException.class, () -> pedidoService.registrarPedido(req, "2"));
        verifyNoInteractions(pedidoRepository);
    }

    @Test
    @DisplayName("PRB-20 Conflicto Producto: Producto no disponible lanza ConflictoOperacionException (409)")
    void crearPedidoProductoNoDisponibleLanzaConflicto() {
        Mesa mesa = new Mesa(1, 1, 4, "Primer piso", EstadoMesa.LIBRE);
        Usuario mozo = new Usuario(2, "mozo_01", "hash", new Rol("MOZO"));
        Categoria cat = new Categoria(1, "Postres");
        Producto prodAgotado = new Producto(15, "Postre", "desc", new BigDecimal("18.00"), false, cat, "url");

        when(mesaRepository.findById(1)).thenReturn(Optional.of(mesa));
        when(usuarioRepository.findById(2)).thenReturn(Optional.of(mozo));
        when(productoRepository.findById(15)).thenReturn(Optional.of(prodAgotado));

        CrearPedidoRequest req = new CrearPedidoRequest(1, List.of(new ItemPedidoRequest(15, 1, null)));

        assertThrows(ConflictoOperacionException.class, () -> pedidoService.registrarPedido(req, "2"));
        verifyNoInteractions(pedidoRepository);
    }
}
