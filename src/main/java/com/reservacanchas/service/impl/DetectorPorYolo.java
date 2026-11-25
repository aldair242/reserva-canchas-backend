package com.reservacanchas.service.impl;

import com.reservacanchas.config.ClockConfig;
import com.reservacanchas.dto.sensor.DeteccionYoloRequest;
import com.reservacanchas.exception.CredencialesInvalidasException;
import com.reservacanchas.exception.OperacionNoPermitidaException;
import com.reservacanchas.exception.ResourceNotFoundException;
import com.reservacanchas.model.CamaraCancha;
import com.reservacanchas.model.Gol;
import com.reservacanchas.model.Grabacion;
import com.reservacanchas.model.Partido;
import com.reservacanchas.model.Sede;
import com.reservacanchas.model.enums.EstadoEvento;
import com.reservacanchas.model.enums.EstadoGrabacion;
import com.reservacanchas.model.enums.EstadoPartido;
import com.reservacanchas.model.enums.LadoEquipo;
import com.reservacanchas.model.enums.OrigenEvento;
import com.reservacanchas.repository.CamaraCanchaRepository;
import com.reservacanchas.repository.EventoPartidoRepository;
import com.reservacanchas.repository.GrabacionRepository;
import com.reservacanchas.repository.PartidoRepository;
import com.reservacanchas.service.ClipService;
import com.reservacanchas.service.IDetectorGol;
import com.reservacanchas.service.SecurityService;
import com.reservacanchas.event.GolDetectadoEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class DetectorPorYolo implements IDetectorGol {

    private static final Logger log = LoggerFactory.getLogger(DetectorPorYolo.class);

    private final String modelo;
    private final double umbralConfianza;
    private final boolean simulado;
    private final int segundosAntes;
    private final int segundosDespues;
    private final int horasLimiteAtribucion;

    private final PartidoRepository partidoRepository;
    private final EventoPartidoRepository eventoRepository;
    private final GrabacionRepository grabacionRepository;
    private final CamaraCanchaRepository camaraCanchaRepository;
    private final ClipService clipService;
    private final SecurityService securityService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public DetectorPorYolo(
            @Value("${yolo.modelo:yolov8-sports.pt}") String modelo,
            @Value("${yolo.umbral-confianza:0.85}") double umbralConfianza,
            @Value("${yolo.simulado:true}") boolean simulado,
            @Value("${clips.segundos-antes:15}") int segundosAntes,
            @Value("${clips.segundos-despues:8}") int segundosDespues,
            @Value("${partidos.horas-limite-atribucion:48}") int horasLimiteAtribucion,
            PartidoRepository partidoRepository,
            EventoPartidoRepository eventoRepository,
            GrabacionRepository grabacionRepository,
            CamaraCanchaRepository camaraCanchaRepository,
            ClipService clipService,
            SecurityService securityService,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.modelo = modelo;
        this.umbralConfianza = umbralConfianza;
        this.simulado = simulado;
        this.segundosAntes = segundosAntes;
        this.segundosDespues = segundosDespues;
        this.horasLimiteAtribucion = horasLimiteAtribucion;
        this.partidoRepository = partidoRepository;
        this.eventoRepository = eventoRepository;
        this.grabacionRepository = grabacionRepository;
        this.camaraCanchaRepository = camaraCanchaRepository;
        this.clipService = clipService;
        this.securityService = securityService;
        this.eventPublisher = eventPublisher;
        this.clock = clock != null ? clock : Clock.system(ClockConfig.ZONA_HORARIA);
    }

    public DetectorPorYolo(
            String modelo,
            double umbralConfianza,
            boolean simulado,
            int segundosAntes,
            int segundosDespues,
            int horasLimiteAtribucion,
            PartidoRepository partidoRepository,
            EventoPartidoRepository eventoRepository,
            GrabacionRepository grabacionRepository,
            CamaraCanchaRepository camaraCanchaRepository,
            ClipService clipService,
            Clock clock) {
        this(modelo, umbralConfianza, simulado, segundosAntes, segundosDespues, horasLimiteAtribucion,
                partidoRepository, eventoRepository, grabacionRepository, camaraCanchaRepository,
                clipService, null, null, clock);
    }

    public String getModelo() {
        return modelo;
    }

    public double getUmbralConfianza() {
        return umbralConfianza;
    }

    public boolean isSimulado() {
        return simulado;
    }

    @Override
    public List<Gol> detectar() {
        List<Partido> partidosEnJuego = partidoRepository.findAll().stream()
                .filter(p -> p.getEstado() == EstadoPartido.EN_JUEGO)
                .toList();

        if (partidosEnJuego.isEmpty()) {
            return Collections.emptyList();
        }

        List<Gol> golesDetectados = new ArrayList<>();
        for (Partido partido : partidosEnJuego) {
            try {
                Gol gol = procesarDeteccionEnPartido(partido, LadoEquipo.A, LocalDateTime.now(clock), 0.92);
                golesDetectados.add(gol);
            } catch (Exception e) {
                log.warn("No se pudo simular deteccion para partido {}: {}", partido.getId(), e.getMessage());
            }
        }
        return golesDetectados;
    }

    @Transactional
    public Gol procesarDeteccionPorCamara(Long camaraId, DeteccionYoloRequest request) {
        return procesarDeteccionPorCamara(camaraId, request, null);
    }

    @Transactional
    public Gol procesarDeteccionPorCamara(Long camaraId, DeteccionYoloRequest request, Authentication authentication) {
        CamaraCancha camara = camaraCanchaRepository.findById(camaraId)
                .orElseThrow(() -> new ResourceNotFoundException("Camara", camaraId));

        validarAutorizacion(camara, request != null ? request.claveApi() : null, authentication);

        if (!camara.isActivo()) {
            throw new OperacionNoPermitidaException("La cámara " + camara.getCodigo() + " se encuentra inactiva");
        }

        camara.registrarLatido();
        camaraCanchaRepository.save(camara);

        Long canchaId = camara.getCancha().getId();
        Partido partido = partidoRepository.findByCanchaIdAndEstado(canchaId, EstadoPartido.EN_JUEGO)
                .orElseThrow(() -> new OperacionNoPermitidaException("No hay ningún partido en juego para la cancha de esta cámara"));

        double confianza = request != null && request.confianza() != null ? request.confianza() : 0.90;
        if (confianza < umbralConfianza) {
            throw new OperacionNoPermitidaException("La confianza de detección (" + confianza
                    + ") es inferior al umbral mínimo requerido (" + umbralConfianza + ")");
        }

        LadoEquipo equipo = request != null && request.equipo() != null ? request.equipo() : LadoEquipo.A;
        LocalDateTime instante = request != null && request.instante() != null ? request.instante() : LocalDateTime.now(clock);

        return procesarDeteccionEnPartido(partido, equipo, instante, confianza);
    }

    @Transactional
    public Gol procesarDeteccionPorPartido(Long partidoId, DeteccionYoloRequest request) {
        return procesarDeteccionPorPartido(partidoId, request, null);
    }

    @Transactional
    public Gol procesarDeteccionPorPartido(Long partidoId, DeteccionYoloRequest request, Authentication authentication) {
        Partido partido = partidoRepository.findById(partidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido", partidoId));

        CamaraCancha camaraAsociada = null;
        if (partido.getReserva() != null && partido.getReserva().getCancha() != null) {
            camaraAsociada = camaraCanchaRepository.findFirstByCanchaIdAndActivoTrue(partido.getReserva().getCancha().getId())
                    .orElse(null);
        }

        validarAutorizacion(camaraAsociada, partido.getReserva() != null && partido.getReserva().getCancha() != null ? partido.getReserva().getCancha().getSede() : null, request != null ? request.claveApi() : null, authentication);

        if (partido.getEstado() != EstadoPartido.EN_JUEGO) {
            throw new OperacionNoPermitidaException("El partido debe estar EN_JUEGO para registrar detecciones en vivo");
        }

        double confianza = request != null && request.confianza() != null ? request.confianza() : 0.90;
        if (confianza < umbralConfianza) {
            throw new OperacionNoPermitidaException("La confianza de detección (" + confianza
                    + ") es inferior al umbral mínimo requerido (" + umbralConfianza + ")");
        }

        LadoEquipo equipo = request != null && request.equipo() != null ? request.equipo() : LadoEquipo.A;
        LocalDateTime instante = request != null && request.instante() != null ? request.instante() : LocalDateTime.now(clock);

        return procesarDeteccionEnPartido(partido, equipo, instante, confianza);
    }

    private void validarAutorizacion(CamaraCancha camara, String claveApi, Authentication authentication) {
        Sede sede = (camara != null && camara.getCancha() != null) ? camara.getCancha().getSede() : null;
        validarAutorizacion(camara, sede, claveApi, authentication);
    }

    private void validarAutorizacion(CamaraCancha camara, Sede sede, String claveApi, Authentication authentication) {
        // 1. Clave de dispositivo válida
        if (camara != null && claveApi != null && !claveApi.isBlank() && camara.validarClave(claveApi)) {
            return;
        }

        // 2. Rol administrador o admin de sede
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            if (securityService == null) {
                return;
            }
            try {
                if (securityService.esAdministrador(authentication)) {
                    return;
                }
                if (sede != null && securityService.esAdministradorSede(authentication)) {
                    securityService.verificarGestionSede(sede, authentication);
                    return;
                }
            } catch (Exception e) {
                throw new AccessDeniedException("No tiene permisos para gestionar la sede de esta detección");
            }
            throw new AccessDeniedException("No tiene permisos para gestionar la detección");
        }

        // 3. Ni clave de dispositivo ni credenciales válidas
        throw new CredencialesInvalidasException("Se requiere rol de administrador de sede o clave API de cámara válida");
    }

    private Gol procesarDeteccionEnPartido(Partido partido, LadoEquipo equipo, LocalDateTime instante, double confianza) {
        // Verificar si ya existe una detección reciente (duplicada) para este partido y equipo
        boolean esDuplicado = eventoRepository.findByPartidoId(partido.getId()).stream()
                .filter(Gol.class::isInstance)
                .map(Gol.class::cast)
                .anyMatch(g -> g.getEquipo() == equipo &&
                        Math.abs(Duration.between(
                                g.getInstante().atZone(ClockConfig.ZONA_HORARIA).toInstant(),
                                instante.atZone(ClockConfig.ZONA_HORARIA).toInstant()).getSeconds()) < 10);
        if (esDuplicado) {
            log.warn("Detección duplicada para partido {} y equipo {}. Ignorando.", partido.getId(), equipo);
            throw new OperacionNoPermitidaException("Detección duplicada: ya existe un gol registrado para el equipo en este lapso temporal");
        }

        Gol gol = new Gol();
        gol.setPartido(partido);
        gol.setEquipo(equipo);
        gol.setInstante(instante);
        gol.setMinuto(gol.calcularMinuto(partido.getInicioReal()));
        gol.setOrigen(OrigenEvento.YOLO);
        gol.setEstado(EstadoEvento.PENDIENTE_ATRIBUCION);
        gol.setFechaLimiteAtribucion(instante.plusHours(horasLimiteAtribucion));

        partido.registrarEvento(gol);
        Gol guardado = eventoRepository.save(gol);

        log.info("Gol detectado por modelo YOLO [{}] (confianza: {}) para el partido {} (Equipo {})",
                modelo, confianza, partido.getId(), equipo);

        Long canchaId = partido.getReserva() != null && partido.getReserva().getCancha() != null
                ? partido.getReserva().getCancha().getId() : null;

        Grabacion grabacion = grabacionRepository.findFirstByPartidoIdAndEstado(partido.getId(), EstadoGrabacion.GRABANDO)
                .orElse(null);

        if (grabacion == null && canchaId != null) {
            CamaraCancha camara = camaraCanchaRepository.findFirstByCanchaIdAndActivoTrue(canchaId)
                    .orElse(null);
            if (camara != null) {
                grabacion = grabacionRepository.save(camara.iniciarGrabacion(partido));
            }
        }

        if (grabacion != null) {
            if (eventPublisher != null) {
                eventPublisher.publishEvent(new GolDetectadoEvent(grabacion, guardado, instante, segundosAntes, segundosDespues));
            } else if (clipService != null) {
                clipService.generarClipAsync(grabacion, guardado, instante, segundosAntes, segundosDespues);
            }
        } else {
            log.warn("No se encontró grabación activa ni cámara disponible para generar el clip del partido {}", partido.getId());
        }

        return guardado;
    }
}
