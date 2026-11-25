package com.reservacanchas.dto.partido;

import com.reservacanchas.model.enums.LadoEquipo;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Solicitud para registrar o agregar un participante al partido")
public record ParticipanteRequest(
        @Schema(description = "ID del usuario jugador registrado (si aplica)", example = "3")
        Long jugadorId,

        @Schema(description = "Nombre de invitado no registrado (si jugadorId es nulo)", example = "Martín")
        String nombreInvitado,

        @Schema(description = "Apellido de invitado no registrado (si jugadorId es nulo)", example = "López")
        String apellidoInvitado,

        @Schema(description = "Equipo asignado", example = "EQUIPO_A", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LadoEquipo equipo,

        @Schema(description = "Token de invitación requerido para unirse al partido", example = "INV-ABC123XYZ")
        String tokenInvitacion
) {
    public ParticipanteRequest(Long jugadorId, String nombreInvitado, String apellidoInvitado, LadoEquipo equipo) {
        this(jugadorId, nombreInvitado, apellidoInvitado, equipo, null);
    }
}
