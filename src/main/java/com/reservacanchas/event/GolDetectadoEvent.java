package com.reservacanchas.event;

import com.reservacanchas.model.Gol;
import com.reservacanchas.model.Grabacion;

import java.time.LocalDateTime;

public record GolDetectadoEvent(
        Grabacion grabacion,
        Gol gol,
        LocalDateTime instante,
        int segundosAntes,
        int segundosDespues
) {
}
