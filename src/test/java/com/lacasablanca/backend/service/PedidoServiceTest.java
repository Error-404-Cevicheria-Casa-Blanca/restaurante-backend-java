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
import java.util.Collections;
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
    @DisplayName("PE-12 Test 1: Creación de pedido calcula total y cambia mesa a OCUPADA")
    void crearPedidoExitosoCalculaTotalYCambiaEstadoMesa() {
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
        assertEquals(EstadoPedido.PENDIENTE, res.getEstado());
        assertEquals(EstadoMesa.OCUPADA, mesa.getEstado());
        verify(mesaRepository).save(mesa);
    }

    @Test
    @DisplayName("PE-12 Test 2: Mesa ocupada lanza ConflictoOperacionException (409)")
    void crearPedidoMesaOcupadaLanzaConflicto() {
        Mesa mesaOcupada = new Mesa(1, 1, 4, "Primer piso", EstadoMesa.OCUPADA);
        when(mesaRepository.findById(1)).thenReturn(Optional.of(mesaOcupada));

        CrearPedidoRequest req = new CrearPedidoRequest(1, List.of(new ItemPedidoRequest(10, 1, null)));

        assertThrows(ConflictoOperacionException.class, () -> pedidoService.registrarPedido(req, "2"));
        verifyNoInteractions(pedidoRepository);
    }

    @Test
    @DisplayName("PE-12 Test 3: Producto inexistente lanza RecursoNoEncontradoException")
    void crearPedidoProductoInexistenteLanzaNotFound() {
        Mesa mesa = new Mesa(1, 1, 4, "Primer piso", EstadoMesa.LIBRE);
        Usuario mozo = new Usuario(2, "mozo_01", "hash", new Rol("MOZO"));

        when(mesaRepository.findById(1)).thenReturn(Optional.of(mesa));
        when(usuarioRepository.findById(2)).thenReturn(Optional.of(mozo));
        when(productoRepository.findById(999)).thenReturn(Optional.empty());

        CrearPedidoRequest req = new CrearPedidoRequest(1, List.of(new ItemPedidoRequest(999, 1, null)));

        assertThrows(RecursoNoEncontradoException.class, () -> pedidoService.registrarPedido(req, "2"));
        verifyNoInteractions(pedidoRepository);
    }

    @Test
    @DisplayName("PE-12 Test 4: Producto no disponible lanza ConflictoOperacionException")
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
