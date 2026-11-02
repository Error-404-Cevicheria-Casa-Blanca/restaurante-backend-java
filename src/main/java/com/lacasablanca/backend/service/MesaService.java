package com.lacasablanca.backend.service;

import com.lacasablanca.backend.dto.MesaDto;
import com.lacasablanca.backend.exception.ConflictoOperacionException;
import com.lacasablanca.backend.exception.RecursoNoEncontradoException;
import com.lacasablanca.backend.model.EstadoMesa;
import com.lacasablanca.backend.model.Mesa;
import com.lacasablanca.backend.model.Usuario;
import com.lacasablanca.backend.repository.MesaRepository;
import com.lacasablanca.backend.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MesaService {

    private final MesaRepository mesaRepository;
    private final UsuarioRepository usuarioRepository;

    public MesaService(MesaRepository mesaRepository, UsuarioRepository usuarioRepository) {
        this.mesaRepository = mesaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<MesaDto> listarMesasOrdenadas() {
        return mesaRepository.findAllByOrderByNumeroAsc().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public MesaDto actualizarEstado(Integer id, EstadoMesa nuevoEstado, String identificadorUsuario) {
        if (nuevoEstado == null) {
            throw new IllegalArgumentException("El estado es obligatorio");
        }
        Mesa mesa = mesaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Mesa no encontrada con ID: " + id));

        EstadoMesa origen = mesa.getEstado();

        // 1. Idempotencia de LIBRE / CUENTA_SOLICITADA o Conflicto para ATENDIDA / OCUPADA
        if (origen == nuevoEstado) {
            if (nuevoEstado == EstadoMesa.LIBRE || nuevoEstado == EstadoMesa.CUENTA_SOLICITADA) {
                return toDto(mesa); // no-op idempotente
            }
            throw new ConflictoOperacionException(
                    "La mesa " + mesa.getNumero() + " ya se encuentra en estado " + origen);
        }

        // 2. Validación de máquina de estados de la mesa (PRB-40)
        if (!esTransicionValida(origen, nuevoEstado)) {
            if ((nuevoEstado == EstadoMesa.ATENDIDA || nuevoEstado == EstadoMesa.OCUPADA) && origen != EstadoMesa.LIBRE) {
                throw new ConflictoOperacionException("La mesa " + mesa.getNumero() + " no está disponible. Estado actual: " + origen);
            }
            throw new ConflictoOperacionException("Transición de estado no permitida: de " + origen + " a " + nuevoEstado + " para la mesa " + mesa.getNumero());
        }

        // 3. Si se abre la mesa desde LIBRE, resolver mozo asignado
        Usuario mozo = null;
        if (origen == EstadoMesa.LIBRE) {
            mozo = resolverUsuario(identificadorUsuario);
        }

        // 4. Bloqueo optimista atómico condicional en base de datos
        int actualizados = mesaRepository.actualizarEstadoCondicional(id, nuevoEstado, origen);
        if (actualizados == 0) {
            EstadoMesa estadoActual = mesaRepository.findEstadoById(id).orElse(origen);
            throw new ConflictoOperacionException(
                    "Conflicto de concurrencia: la mesa " + mesa.getNumero() +
                    " cambió a estado " + estadoActual + " por otra operación");
        }

        // 5. Registrar marcas de tiempo y mozo responsable
        LocalDateTime ahora = LocalDateTime.now();
        if (origen == EstadoMesa.LIBRE &&
                (nuevoEstado == EstadoMesa.ATENDIDA || nuevoEstado == EstadoMesa.OCUPADA)) {
            mesa.setMozo(mozo);
            mesa.setFechaApertura(ahora);
            mesa.setFechaCierre(null);
        } else if (nuevoEstado == EstadoMesa.LIBRE) {
            mesa.setFechaCierre(ahora);
        }

        mesa.setEstado(nuevoEstado);
        return toDto(mesa);
    }

    private Usuario resolverUsuario(String identificador) {
        if (identificador == null || identificador.isBlank()) {
            throw new RecursoNoEncontradoException("Usuario no autenticado o sesión inválida");
        }
        try {
            Integer id = Integer.parseInt(identificador);
            return usuarioRepository.findById(id)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Usuario mozo no encontrado con ID: " + id));
        } catch (NumberFormatException e) {
            return usuarioRepository.findByUsername(identificador)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Usuario mozo no encontrado: " + identificador));
        }
    }

    /**
     * Máquina de estados formal de Mesa (PRB-40):
     * - LIBRE -> ATENDIDA (mozo abre comanda), OCUPADA (pedido directo)
     * - ATENDIDA -> LIBRE (cancelación o liberación segura PRB-39), OCUPADA (confirmación de comanda)
     * - OCUPADA -> CUENTA_SOLICITADA (cliente pide precuenta), LIBRE (liberación administrativa)
     * - CUENTA_SOLICITADA -> LIBRE (cobro exitoso y liberación), OCUPADA (cliente continúa pidiendo)
     */
    public boolean esTransicionValida(EstadoMesa origen, EstadoMesa destino) {
        if (origen == null || destino == null || origen == destino) {
            return false;
        }
        return switch (origen) {
            case LIBRE -> destino == EstadoMesa.ATENDIDA || destino == EstadoMesa.OCUPADA;
            case ATENDIDA -> destino == EstadoMesa.LIBRE || destino == EstadoMesa.OCUPADA;
            case OCUPADA -> destino == EstadoMesa.CUENTA_SOLICITADA || destino == EstadoMesa.LIBRE;
            case CUENTA_SOLICITADA -> destino == EstadoMesa.LIBRE || destino == EstadoMesa.OCUPADA;
        };
    }

    private MesaDto toDto(Mesa m) {
        return new MesaDto(m.getId(), m.getNumero(), m.getCapacidad(), m.getUbicacion(), m.getEstado());
    }
}
