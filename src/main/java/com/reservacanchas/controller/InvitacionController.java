package com.reservacanchas.controller;

import com.reservacanchas.dto.partido.InvitacionResponse;
import com.reservacanchas.dto.partido.ParticipanteResponse;
import com.reservacanchas.dto.partido.UnirsePartidoRequest;
import com.reservacanchas.service.InvitacionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invitaciones")
@Tag(name = "Invitaciones", description = "Endpoints publicos y privados de invitacion y vinculacion a partidos")
public class InvitacionController {

    private final InvitacionService invitacionService;

    public InvitacionController(InvitacionService invitacionService) {
        this.invitacionService = invitacionService;
    }

    @GetMapping("/{token}")
    @Operation(summary = "Obtener resumen publico de un partido por token", description = "Permite a cualquier usuario o invitado consultar datos generales del partido antes de unirse.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resumen del partido obtenido exitosamente"),
            @ApiResponse(responseCode = "404", description = "Token de invitacion no valido o expirado")
    })
    public ResponseEntity<InvitacionResponse> obtenerResumen(@PathVariable String token) {
        return ResponseEntity.ok(invitacionService.obtenerResumen(token));
    }

    @PostMapping("/{token}/unirse")
    @Operation(summary = "Unirse al partido mediante enlace de invitación", description = "Permite a un jugador autenticado o a un invitado (con nombre y apellido) unirse a un equipo del partido, validando límites de capacidad y previniendo registros duplicados.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Unido al partido exitosamente"),
            @ApiResponse(responseCode = "400", description = "Equipo lleno, capacidad máxima alcanzada o usuario ya inscrito"),
            @ApiResponse(responseCode = "404", description = "Token de invitación no válido o expirado")
    })
    public ResponseEntity<ParticipanteResponse> unirse(
            @PathVariable String token,
            @Valid @RequestBody UnirsePartidoRequest request,
            Authentication authentication) {
        ParticipanteResponse response = invitacionService.unirseAPartido(token, request, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{token}/reclamar")
    @Operation(summary = "Reclamar el perfil de un invitado", description = "Permite a un jugador registrado vincular a su cuenta las estadisticas y participaciones previas acumuladas como invitado.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Perfil reclamado exitosamente"),
            @ApiResponse(responseCode = "400", description = "El perfil ya fue reclamado previamente"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "404", description = "Token de reclamo no encontrado")
    })
    public ResponseEntity<Void> reclamar(@PathVariable String token,
                                          Authentication authentication) {
        invitacionService.reclamarPerfil(token, authentication);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/partidos/{partidoId}/participantes/{participanteId}/enlace-reclamo")
    @Operation(summary = "Obtener enlace de reclamo de un invitado", description = "Entrega al organizador del partido el enlace de reclamo para compartirlo con el invitado no registrado.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<java.util.Map<String, String>> obtenerEnlaceReclamo(
            @PathVariable Long partidoId,
            @PathVariable Long participanteId,
            Authentication authentication) {
        String enlace = invitacionService.obtenerEnlaceReclamo(partidoId, participanteId, authentication);
        return ResponseEntity.ok(java.util.Map.of("enlaceReclamo", enlace));
    }
}
