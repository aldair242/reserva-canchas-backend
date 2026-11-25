package com.reservacanchas.dto.sensor;

import com.reservacanchas.model.enums.LadoEquipo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Schema(description = "Solicitud de simulación o ingesta de detección de gol por visión artificial (YOLO)")
public record DeteccionYoloRequest(
        @Schema(description = "Equipo al que se le adjudica el gol detectado", example = "A", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        LadoEquipo equipo,

        @Schema(description = "Marca temporal del gol detectado en el video (opcional, por defecto instante actual)", example = "2026-10-15T18:35:00")
        LocalDateTime instante,

        @Schema(description = "Nivel de confianza de la detección YOLO (ejemplo 0.92)", example = "0.92")
        Double confianza,

        @Schema(description = "Clave API de la cámara o dispositivo (opcional si la petición está autenticada con JWT de Admin de Sede)", example = "cam-api-key-12345")
        String claveApi
) {
    public DeteccionYoloRequest(LadoEquipo equipo, LocalDateTime instante, Double confianza) {
        this(equipo, instante, confianza, null);
    }
}

