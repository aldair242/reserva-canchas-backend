package com.reservacanchas.service;

import com.reservacanchas.dto.partido.ParticipanteRequest;
import com.reservacanchas.dto.partido.PartidoResponse;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface PartidoService {

    PartidoResponse crearDesdeReserva(Long reservaId, Authentication authentication);

    PartidoResponse obtenerPorId(Long id);

    PartidoResponse obtenerPorId(Long id, Authentication authentication);

    List<PartidoResponse> listarMisPartidos(Authentication authentication);

    org.springframework.data.domain.Page<PartidoResponse> listarTodos(
            Long sedeId,
            java.time.LocalDate fecha,
            com.reservacanchas.model.enums.EstadoPartido estado,
            org.springframework.data.domain.Pageable pageable,
            Authentication authentication);

    PartidoResponse agregarParticipante(Long partidoId, ParticipanteRequest request,
                                        Authentication authentication);

    PartidoResponse iniciar(Long id, Authentication authentication);

    PartidoResponse finalizar(Long id, Authentication authentication);

    PartidoResponse revocarTokenInvitacion(Long partidoId, Authentication authentication);
}
