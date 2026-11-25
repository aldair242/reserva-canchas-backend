package com.reservacanchas.service;

import com.reservacanchas.dto.partido.InvitacionResponse;
import com.reservacanchas.dto.partido.ParticipanteResponse;
import com.reservacanchas.dto.partido.UnirsePartidoRequest;
import org.springframework.security.core.Authentication;

public interface InvitacionService {

    InvitacionResponse obtenerResumen(String token);

    ParticipanteResponse unirseAPartido(String token, UnirsePartidoRequest request, Authentication authentication);

    void reclamarPerfil(String token, Authentication authentication);

    String obtenerEnlaceReclamo(Long partidoId, Long participanteId, Authentication authentication);
}
