package com.reservacanchas.sensor;

import com.reservacanchas.config.ClockConfig;
import com.reservacanchas.dto.sensor.DeteccionYoloRequest;
import com.reservacanchas.event.GolDetectadoEvent;
import com.reservacanchas.exception.CredencialesInvalidasException;
import com.reservacanchas.exception.OperacionNoPermitidaException;
import com.reservacanchas.model.CamaraCancha;
import com.reservacanchas.model.CanchaFutbol;
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
import com.reservacanchas.service.SecurityService;
import com.reservacanchas.service.impl.DetectorPorYolo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DetectorPorYoloTest {

    @Mock
    private PartidoRepository partidoRepository;

    @Mock
    private EventoPartidoRepository eventoRepository;

    @Mock
    private GrabacionRepository grabacionRepository;

    @Mock
    private CamaraCanchaRepository camaraCanchaRepository;

    @Mock
    private ClipService clipService;

    @Mock
    private SecurityService securityService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private Authentication authentication;

    private Clock clock;
    private DetectorPorYolo detector;

    @BeforeEach
    void setUp() {
        clock = Clock.system(ClockConfig.ZONA_HORARIA);
        detector = new DetectorPorYolo(
                "yolov8-sports.pt",
                0.85,
                true,
                15,
                8,
                48,
                partidoRepository,
                eventoRepository,
                grabacionRepository,
                camaraCanchaRepository,
                clipService,
                securityService,
                eventPublisher,
                clock
        );
    }

    @Test
    void procesarDeteccionPorCamara_sinClaveNiAuth_lanzaCredencialesInvalidas() {
        Long camaraId = 5L;
        CamaraCancha camara = new CamaraCancha();
        camara.setId(camaraId);
        camara.setActivo(true);
        camara.setClaveApiHash("hashed_clave");

        when(camaraCanchaRepository.findById(camaraId)).thenReturn(Optional.of(camara));

        DeteccionYoloRequest request = new DeteccionYoloRequest(LadoEquipo.A, LocalDateTime.now(clock), 0.90, null);

        assertThrows(CredencialesInvalidasException.class,
                () -> detector.procesarDeteccionPorCamara(camaraId, request, null));
    }

    @Test
    void procesarDeteccionPorCamara_usuarioSinPermiso_lanzaAccessDenied() {
        Long camaraId = 5L;
        Sede sede = new Sede();
        sede.setId(1L);
        CanchaFutbol cancha = new CanchaFutbol();
        cancha.setSede(sede);

        CamaraCancha camara = new CamaraCancha();
        camara.setId(camaraId);
        camara.setActivo(true);
        camara.setCancha(cancha);

        when(camaraCanchaRepository.findById(camaraId)).thenReturn(Optional.of(camara));
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("usuario_regular");
        when(securityService.esAdministrador(authentication)).thenReturn(false);
        when(securityService.esAdministradorSede(authentication)).thenReturn(true);
        doThrow(new AccessDeniedException("No administra esta sede"))
                .when(securityService).verificarGestionSede(sede, authentication);

        DeteccionYoloRequest request = new DeteccionYoloRequest(LadoEquipo.A, LocalDateTime.now(clock), 0.90, "clave_incorrecta");

        assertThrows(AccessDeniedException.class,
                () -> detector.procesarDeteccionPorCamara(camaraId, request, authentication));
    }

    @Test
    void procesarDeteccionPorCamara_conClaveApiValida_creaGol() {
        Long camaraId = 5L;
        CanchaFutbol cancha = new CanchaFutbol();
        cancha.setId(20L);

        CamaraCancha camara = new CamaraCancha();
        camara.setId(camaraId);
        camara.setActivo(true);
        camara.setCodigo("CAM-01");
        camara.setClaveApiHash(org.springframework.security.crypto.bcrypt.BCrypt.hashpw("cam-api-key", org.springframework.security.crypto.bcrypt.BCrypt.gensalt()));
        camara.setCancha(cancha);

        Partido partido = new Partido();
        partido.setId(50L);
        partido.setEstado(EstadoPartido.EN_JUEGO);
        partido.setInicioReal(LocalDateTime.of(2026, Month.OCTOBER, 15, 19, 0));

        when(camaraCanchaRepository.findById(camaraId)).thenReturn(Optional.of(camara));
        when(partidoRepository.findByCanchaIdAndEstado(20L, EstadoPartido.EN_JUEGO)).thenReturn(Optional.of(partido));
        when(eventoRepository.findByPartidoId(50L)).thenReturn(Collections.emptyList());
        when(eventoRepository.save(any(Gol.class))).thenAnswer(inv -> inv.getArgument(0));

        DeteccionYoloRequest request = new DeteccionYoloRequest(LadoEquipo.B, LocalDateTime.now(clock), 0.90, "cam-api-key");

        Gol gol = detector.procesarDeteccionPorCamara(camaraId, request, null);

        assertNotNull(gol);
        assertEquals(LadoEquipo.B, gol.getEquipo());
        assertEquals(OrigenEvento.YOLO, gol.getOrigen());
    }

    @Test
    void procesarDeteccionPorPartido_partidoEnJuegoYAdmin_publicaEventoPostCommit() {
        Long partidoId = 10L;
        Partido partido = new Partido();
        partido.setId(partidoId);
        partido.setEstado(EstadoPartido.EN_JUEGO);
        partido.setInicioReal(LocalDateTime.of(2026, Month.OCTOBER, 15, 18, 0));

        Grabacion grabacion = new Grabacion();
        grabacion.setId(100L);
        grabacion.setEstado(EstadoGrabacion.GRABANDO);

        when(partidoRepository.findById(partidoId)).thenReturn(Optional.of(partido));
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("admin");
        when(securityService.esAdministrador(authentication)).thenReturn(true);
        when(eventoRepository.findByPartidoId(partidoId)).thenReturn(Collections.emptyList());
        when(eventoRepository.save(any(Gol.class))).thenAnswer(inv -> inv.getArgument(0));
        when(grabacionRepository.findFirstByPartidoIdAndEstado(partidoId, EstadoGrabacion.GRABANDO))
                .thenReturn(Optional.of(grabacion));

        LocalDateTime instante = LocalDateTime.of(2026, Month.OCTOBER, 15, 18, 25);
        DeteccionYoloRequest request = new DeteccionYoloRequest(LadoEquipo.A, instante, 0.94);

        Gol resultado = detector.procesarDeteccionPorPartido(partidoId, request, authentication);

        assertNotNull(resultado);
        assertEquals(LadoEquipo.A, resultado.getEquipo());
        assertEquals(OrigenEvento.YOLO, resultado.getOrigen());
        assertEquals(EstadoEvento.PENDIENTE_ATRIBUCION, resultado.getEstado());
        assertEquals(25, resultado.getMinuto());

        verify(eventPublisher).publishEvent(any(GolDetectadoEvent.class));
    }

    @Test
    void procesarDeteccionPorPartido_deteccionDuplicada_lanzaExcepcion() {
        Long partidoId = 10L;
        Partido partido = new Partido();
        partido.setId(partidoId);
        partido.setEstado(EstadoPartido.EN_JUEGO);

        LocalDateTime instante = LocalDateTime.of(2026, Month.OCTOBER, 15, 18, 25, 0);

        Gol golExistente = new Gol();
        golExistente.setEquipo(LadoEquipo.A);
        golExistente.setInstante(instante.minusSeconds(3));

        when(partidoRepository.findById(partidoId)).thenReturn(Optional.of(partido));
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("admin");
        when(securityService.esAdministrador(authentication)).thenReturn(true);
        when(eventoRepository.findByPartidoId(partidoId)).thenReturn(List.of(golExistente));

        DeteccionYoloRequest request = new DeteccionYoloRequest(LadoEquipo.A, instante, 0.94);

        OperacionNoPermitidaException ex = assertThrows(OperacionNoPermitidaException.class,
                () -> detector.procesarDeteccionPorPartido(partidoId, request, authentication));
        assertEquals("Detección duplicada: ya existe un gol registrado para el equipo en este lapso temporal", ex.getMessage());
    }

    @Test
    void procesarDeteccionPorPartido_confianzaInferiorAlUmbral_lanzaExcepcion() {
        Long partidoId = 10L;
        Partido partido = new Partido();
        partido.setId(partidoId);
        partido.setEstado(EstadoPartido.EN_JUEGO);

        when(partidoRepository.findById(partidoId)).thenReturn(Optional.of(partido));
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("admin");
        when(securityService.esAdministrador(authentication)).thenReturn(true);

        DeteccionYoloRequest request = new DeteccionYoloRequest(LadoEquipo.B, LocalDateTime.now(clock), 0.70);

        assertThrows(OperacionNoPermitidaException.class,
                () -> detector.procesarDeteccionPorPartido(partidoId, request, authentication));
    }

    @Test
    void detectar_interfazGeneral_detectaGolesEnPartidosEnJuego() {
        Partido partido = new Partido();
        partido.setId(1L);
        partido.setEstado(EstadoPartido.EN_JUEGO);
        partido.setInicioReal(LocalDateTime.of(2026, Month.OCTOBER, 15, 10, 0));

        when(partidoRepository.findAll()).thenReturn(List.of(partido));
        when(eventoRepository.findByPartidoId(1L)).thenReturn(Collections.emptyList());
        when(eventoRepository.save(any(Gol.class))).thenAnswer(inv -> inv.getArgument(0));

        List<Gol> detectados = detector.detectar();

        assertNotNull(detectados);
        assertEquals(1, detectados.size());
        assertEquals(OrigenEvento.YOLO, detectados.get(0).getOrigen());
    }

    @Test
    void procesarDeteccionPorPartido_adminSedeDeOtraSede_lanzaAccessDenied() {
        Long partidoId = 10L;
        Sede sede = new Sede();
        sede.setId(1L);
        CanchaFutbol cancha = new CanchaFutbol();
        cancha.setId(20L);
        cancha.setSede(sede);
        com.reservacanchas.model.Reserva reserva = new com.reservacanchas.model.Reserva();
        reserva.setCancha(cancha);

        Partido partido = new Partido();
        partido.setId(partidoId);
        partido.setReserva(reserva);
        partido.setEstado(EstadoPartido.EN_JUEGO);

        when(partidoRepository.findById(partidoId)).thenReturn(Optional.of(partido));
        when(camaraCanchaRepository.findFirstByCanchaIdAndActivoTrue(20L)).thenReturn(Optional.empty());
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("admin_otra_sede");
        when(securityService.esAdministrador(authentication)).thenReturn(false);
        when(securityService.esAdministradorSede(authentication)).thenReturn(true);
        doThrow(new AccessDeniedException("No administra esta sede"))
                .when(securityService).verificarGestionSede(sede, authentication);

        DeteccionYoloRequest request = new DeteccionYoloRequest(LadoEquipo.A, LocalDateTime.now(clock), 0.95);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> detector.procesarDeteccionPorPartido(partidoId, request, authentication));
        assertEquals("No tiene permisos para gestionar la sede de esta detección", ex.getMessage());
    }
}
