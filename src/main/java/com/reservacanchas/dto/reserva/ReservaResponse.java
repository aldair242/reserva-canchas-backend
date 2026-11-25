package com.reservacanchas.dto.reserva;

import com.reservacanchas.model.enums.EstadoReserva;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = "Detalle de una reserva de cancha")
public record ReservaResponse(
        @Schema(description = "Identificador unico de la reserva", example = "10")
        Long id,

        @Schema(description = "Codigo de confirmacion alfanumerico", example = "RES-A1B2C3D4")
        String codigo,

        @Schema(description = "Fecha de la reserva", example = "2026-10-20")
        LocalDate fecha,

        @Schema(description = "Hora de inicio", example = "19:00:00")
        LocalTime horaInicio,

        @Schema(description = "Hora de fin", example = "20:00:00")
        LocalTime horaFin,

        @Schema(description = "Estado de la reserva (PENDIENTE, CONFIRMADA, CANCELADA, COMPLETADA, NO_SHOW)", example = "PENDIENTE")
        EstadoReserva estado,

        @Schema(description = "Monto total calculado segun la tarifa de la cancha", example = "80.00")
        BigDecimal precioTotal,

        @Schema(description = "Motivo de cancelacion en caso de haber sido cancelada", example = "Lluvia torrencial")
        String motivoCancelacion,

        @Schema(description = "Id del jugador organizador", example = "1")
        Long organizadorId,

        @Schema(description = "Nombre completo del jugador organizador", example = "Lionel Messi")
        String organizadorNombre,

        @Schema(description = "Id de la cancha reservada", example = "1")
        Long canchaId,

        @Schema(description = "Nombre de la cancha reservada", example = "Cancha Principal Futbol 7")
        String canchaNombre,

        @Schema(description = "Indica si la reserva ya cuenta con pago exitoso registrado", example = "false")
        boolean pagada,

        @Schema(description = "Id del partido asociado si ya fue creado, o null", example = "5")
        Long partidoId,

        @Schema(description = "Fecha y hora límite para abonar el saldo antes de la cancelación automática (15 minutos)", example = "2026-10-20T19:15:00")
        java.time.LocalDateTime fechaLimitePago,

        @Schema(description = "Saldo restante pendiente de pago para confirmar la reserva", example = "0.00")
        BigDecimal saldoPendiente,

        @Schema(description = "Monto total abonado efectivamente a la reserva", example = "80.00")
        BigDecimal montoPagado,

        @Schema(description = "Monto total reembolsado a causa de cancelaciones o variaciones favorables de tarifa", example = "0.00")
        BigDecimal montoReembolsado
) {
    public ReservaResponse(Long id, String codigo, LocalDate fecha, LocalTime horaInicio,
                           LocalTime horaFin, EstadoReserva estado, BigDecimal precioTotal,
                           String motivoCancelacion, Long organizadorId, String organizadorNombre,
                           Long canchaId, String canchaNombre, boolean pagada, Long partidoId) {
        this(id, codigo, fecha, horaInicio, horaFin, estado, precioTotal, motivoCancelacion,
                organizadorId, organizadorNombre, canchaId, canchaNombre, pagada, partidoId,
                null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
