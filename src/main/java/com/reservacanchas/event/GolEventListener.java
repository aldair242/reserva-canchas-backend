package com.reservacanchas.event;

import com.reservacanchas.service.ClipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class GolEventListener {

    private static final Logger log = LoggerFactory.getLogger(GolEventListener.class);

    private final ClipService clipService;

    public GolEventListener(ClipService clipService) {
        this.clipService = clipService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onGolDetectado(GolDetectadoEvent event) {
        log.info("Disparando generación asíncrona de clip tras commit para gol {}", event.gol() != null ? event.gol().getId() : null);
        clipService.generarClipAsync(
                event.grabacion(),
                event.gol(),
                event.instante(),
                event.segundosAntes(),
                event.segundosDespues()
        );
    }
}
