package com.reservacanchas.model.tarifa;

import com.reservacanchas.model.enums.TipoTarifa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public final class TarifaFactory {

    public record ParametrosTarifa(
            TipoTarifa tipo,
            BigDecimal precioHora,
            BigDecimal precioNormal,
            BigDecimal precioPico,
            LocalTime inicioPico,
            LocalTime finPico,
            TipoTarifa tipoBase,
            double porcentajeDescuento,
            LocalDate vigenteDesde,
            LocalDate vigenteHasta
    ) {}

    private TarifaFactory() {
    }

    public static EstrategiaTarifa crear(ParametrosTarifa params) {
        if (params == null || params.tipo() == null) {
            BigDecimal precio = params != null && params.precioHora() != null
                    ? params.precioHora() : BigDecimal.ZERO;
            return new TarifaEstandar(precio);
        }

        return switch (params.tipo()) {
            case ESTANDAR -> new TarifaEstandar(
                    params.precioHora() != null ? params.precioHora() : BigDecimal.ZERO);
            case HORA_PICO -> new TarifaHoraPico(
                    params.precioNormal() != null ? params.precioNormal() : BigDecimal.ZERO,
                    params.precioPico() != null ? params.precioPico() : BigDecimal.ZERO,
                    params.inicioPico() != null ? params.inicioPico() : LocalTime.MIN,
                    params.finPico() != null ? params.finPico() : LocalTime.MAX);
            case PROMOCIONAL -> {
                ParametrosTarifa baseParams = new ParametrosTarifa(
                        params.tipoBase() != null ? params.tipoBase() : TipoTarifa.ESTANDAR,
                        params.precioHora(), params.precioNormal(), params.precioPico(),
                        params.inicioPico(), params.finPico(), null, 0, null, null);
                EstrategiaTarifa base = crear(baseParams);
                yield new TarifaPromocional(base, params.porcentajeDescuento(),
                        params.vigenteDesde(), params.vigenteHasta());
            }
        };
    }
}
