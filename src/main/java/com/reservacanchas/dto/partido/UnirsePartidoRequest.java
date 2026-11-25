package com.reservacanchas.dto.partido;

import com.reservacanchas.model.enums.LadoEquipo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Solicitud para unirse a un partido mediante enlace/token de invitación")
public record UnirsePartidoRequest(
        @Schema(description = "Equipo al que desea unirse (A o B)", example = "A", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        LadoEquipo equipo,

        @Schema(description = "Nombre (requerido si no está autenticado como jugador)", example = "Carlos")
        String nombre,

        @Schema(description = "Apellido (requerido si no está autenticado como jugador)", example = "Mendoza")
        String apellido
) {}
