package com.reservacanchas.partido;

import com.reservacanchas.config.ClockConfig;
import com.reservacanchas.dto.partido.EventoRequest;
import com.reservacanchas.dto.partido.ParticipanteRequest;
import com.reservacanchas.dto.partido.PartidoResponse;
import com.reservacanchas.exception.OperacionNoPermitidaException;
import com.reservacanchas.mapper.EventoMapper;
import com.reservacanchas.mapper.PartidoMapper;
import com.reservacanchas.model.AdministradorSede;
import com.reservacanchas.model.CanchaFutbol;
import com.reservacanchas.model.Gol;
import com.reservacanchas.model.Jugador;
import com.reservacanchas.model.ParticipantePartido;
import com.reservacanchas.model.Partido;
import com.reservacanchas.model.Reserva;
import com.reservacanchas.model.Sede;
import com.reservacanchas.model.enums.EstadoEvento;
import com.reservacanchas.model.enums.EstadoPartido;
import com.reservacanchas.model.enums.LadoEquipo;
import com.reservacanchas.model.enums.TipoEvento;
import com.reservacanchas.repository.CamaraCanchaRepository;
import com.reservacanchas.repository.EventoPartidoRepository;
import com.reservacanchas.repository.GrabacionRepository;
import com.reservacanchas.repository.InvitadoRepository;
import com.reservacanchas.repository.JugadorRepository;
import com.reservacanchas.repository.PartidoRepository;
import com.reservacanchas.repository.ReservaRepository;
import com.reservacanchas.repository.SedeRepository;
import com.reservacanchas.service.SecurityService;
import com.reservacanchas.service.impl.EventoServiceImpl;
import com.reservacanchas.service.impl.PartidoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartidosEventosSeguridadTest {

    @Mock
    private PartidoRepository partidoRepository;

    @Mock
    private EventoPartidoRepository eventoRepository;

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private SedeRepository sedeRepository;

    @Mock
    private JugadorRepository jugadorRepository;

    @Mock
    private InvitadoRepository invitadoRepository;

    @Mock
    private CamaraCanchaRepository camaraCanchaRepository;

    @Mock
    private GrabacionRepository grabacionRepository;

    @Mock
    private PartidoMapper partidoMapper;

    @Mock
    private EventoMapper eventoMapper;

    @Mock
    private SecurityService securityService;

    @Mock
    private Authentication authentication;

    private Clock clock;
    private PartidoServiceImpl partidoService;
    private EventoServiceImpl eventoService;

    private Partido partido;
    private Reserva reserva;
    private Sede sede;
    private Jugador organizador;
    private Jugador jugadorExterno;
    private AdministradorSede adminSedeAjena;

    @BeforeEach
    void setUp() {
        clock = Clock.system(ClockConfig.ZONA_HORARIA);

        partidoService = new PartidoServiceImpl(
                partidoRepository,
                reservaRepository,
                sedeRepository,
                jugadorRepository,
                invitadoRepository,
                camaraCanchaRepository,
                grabacionRepository,
                partidoMapper,
                securityService
        );

        eventoService = new EventoServiceImpl(
                eventoRepository,
                partidoRepository,
                eventoMapper,
                securityService,
                clock
        );

        organizador = new Jugador();
        organizador.setId(1L);

        jugadorExterno = new Jugador();
        jugadorExterno.setId(99L);

        adminSedeAjena = new AdministradorSede();
        adminSedeAjena.setId(88L);
        adminSedeAjena.setCorreo("admin.sede2@canchas.com");

        sede = new Sede();
        sede.setId(100L);
        sede.setNombre("Sede Central");

        CanchaFutbol cancha = new CanchaFutbol();
        cancha.setId(20L);
        cancha.setSede(sede);

        reserva = new Reserva();
        reserva.setId(10L);
        reserva.setOrganizador(organizador);
        reserva.setCancha(cancha);

        partido = new Partido();
        partido.setId(5L);
        partido.setReserva(reserva);
        partido.setEstado(EstadoPartido.EN_JUEGO);
        partido.setTokenInvitacion("INV-SECRET-TOKEN");
        partido.setParticipantes(new ArrayList<>());
    }

    @Test
    void registrarEvento_usuarioNoAutorizado_lanzaAccessDenied() {
        when(partidoRepository.findById(5L)).thenReturn(Optional.of(partido));
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("jugadorExterno");
        when(securityService.esAdministrador(authentication)).thenReturn(false);
        when(securityService.getUsuarioActual(authentication)).thenReturn(jugadorExterno);

        EventoRequest request = new EventoRequest(TipoEvento.GOL, LadoEquipo.A, LocalDateTime.now(clock), 10, null, null);

        assertThrows(AccessDeniedException.class,
                () -> eventoService.registrarEvento(5L, request, authentication));
    }

    @Test
    void obtenerPorId_usuarioNoAutorizado_enmascaraTokenInvitacion() {
        when(partidoRepository.findById(5L)).thenReturn(Optional.of(partido));
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("jugadorExterno");
        when(securityService.esAdministrador(authentication)).thenReturn(false);
        when(securityService.getUsuarioActual(authentication)).thenReturn(jugadorExterno);

        PartidoResponse mapperResponse = new PartidoResponse(
                5L, EstadoPartido.EN_JUEGO, "A", "B", 0, 0, null, null,
                "INV-SECRET-TOKEN", 10L, Collections.emptyList(), Collections.emptyList()
        );
        when(partidoMapper.toResponse(partido)).thenReturn(mapperResponse);

        PartidoResponse resp = partidoService.obtenerPorId(5L, authentication);

        assertNotNull(resp);
        assertNull(resp.tokenInvitacion(), "El token de invitación debe ser nulo para usuarios no autorizados");
    }

    @Test
    void obtenerPorId_organizador_veTokenInvitacion() {
        when(partidoRepository.findById(5L)).thenReturn(Optional.of(partido));
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("organizador");
        when(securityService.esAdministrador(authentication)).thenReturn(false);
        when(securityService.getUsuarioActual(authentication)).thenReturn(organizador);

        PartidoResponse mapperResponse = new PartidoResponse(
                5L, EstadoPartido.EN_JUEGO, "A", "B", 0, 0, null, null,
                "INV-SECRET-TOKEN", 10L, Collections.emptyList(), Collections.emptyList()
        );
        when(partidoMapper.toResponse(partido)).thenReturn(mapperResponse);

        PartidoResponse resp = partidoService.obtenerPorId(5L, authentication);

        assertNotNull(resp);
        assertEquals("INV-SECRET-TOKEN", resp.tokenInvitacion());
    }

    @Test
    void agregarParticipante_jugadorSinToken_lanzaOperacionNoPermitida() {
        when(partidoRepository.findById(5L)).thenReturn(Optional.of(partido));
        when(securityService.getUsuarioActual(authentication)).thenReturn(jugadorExterno);

        ParticipanteRequest request = new ParticipanteRequest(99L, null, null, LadoEquipo.B, null);

        OperacionNoPermitidaException ex = assertThrows(OperacionNoPermitidaException.class,
                () -> partidoService.agregarParticipante(5L, request, authentication));
        assertEquals("Se requiere un token de invitación válido para unirse al partido", ex.getMessage());
    }

    @Test
    void agregarParticipante_jugadorConTokenInvalido_lanzaOperacionNoPermitida() {
        when(partidoRepository.findById(5L)).thenReturn(Optional.of(partido));
        when(securityService.getUsuarioActual(authentication)).thenReturn(jugadorExterno);

        ParticipanteRequest request = new ParticipanteRequest(99L, null, null, LadoEquipo.B, "INV-WRONG");

        OperacionNoPermitidaException ex = assertThrows(OperacionNoPermitidaException.class,
                () -> partidoService.agregarParticipante(5L, request, authentication));
        assertEquals("Se requiere un token de invitación válido para unirse al partido", ex.getMessage());
    }

    @Test
    void listarTodos_adminSedeDeOtraSede_lanzaAccessDenied() {
        Sede sedeAjena = new Sede();
        sedeAjena.setId(200L);

        when(securityService.esAdministrador(authentication)).thenReturn(false);
        when(securityService.esAdministradorSede(authentication)).thenReturn(true);
        when(sedeRepository.findById(200L)).thenReturn(Optional.of(sedeAjena));
        doThrow(new AccessDeniedException("No tiene permisos para gestionar esta sede"))
                .when(securityService).verificarGestionSede(sedeAjena, authentication);

        assertThrows(AccessDeniedException.class,
                () -> partidoService.listarTodos(200L, null, null, null, authentication));
    }

    @Test
    void obtenerPorId_adminSedeDeOtraSede_enmascaraTokenInvitacion() {
        when(partidoRepository.findById(5L)).thenReturn(Optional.of(partido));
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("adminSedeAjena");
        when(securityService.esAdministrador(authentication)).thenReturn(false);
        when(securityService.esAdministradorSede(authentication)).thenReturn(true);
        doThrow(new AccessDeniedException("No tiene permisos para gestionar esta sede"))
                .when(securityService).verificarGestionSede(sede, authentication);

        PartidoResponse mapperResponse = new PartidoResponse(
                5L, EstadoPartido.EN_JUEGO, "A", "B", 0, 0, null, null,
                "INV-SECRET-TOKEN", 10L, Collections.emptyList(), Collections.emptyList()
        );
        when(partidoMapper.toResponse(partido)).thenReturn(mapperResponse);

        PartidoResponse resp = partidoService.obtenerPorId(5L, authentication);

        assertNotNull(resp);
        assertNull(resp.tokenInvitacion(), "El token de invitación debe ser nulo para ADMIN_SEDE de otra sede");
    }

    @Test
    void registrarEvento_adminSedeDeOtraSede_lanzaAccessDenied() {
        when(partidoRepository.findById(5L)).thenReturn(Optional.of(partido));
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("adminSedeAjena");
        when(securityService.esAdministrador(authentication)).thenReturn(false);
        when(securityService.esAdministradorSede(authentication)).thenReturn(true);
        doThrow(new AccessDeniedException("No tiene permisos para gestionar esta sede"))
                .when(securityService).verificarGestionSede(sede, authentication);
        when(securityService.getUsuarioActual(authentication)).thenReturn(adminSedeAjena);

        EventoRequest request = new EventoRequest(TipoEvento.GOL, LadoEquipo.A, LocalDateTime.now(clock), 10, null, null);

        assertThrows(AccessDeniedException.class,
                () -> eventoService.registrarEvento(5L, request, authentication));
    }

    @Test
    void gestionarPartido_adminSedeDeOtraSede_lanzaOperacionNoPermitida() {
        when(securityService.getUsuarioActual(authentication)).thenReturn(adminSedeAjena);

        assertThrows(OperacionNoPermitidaException.class,
                () -> partidoService.iniciar(5L, authentication));
        assertThrows(OperacionNoPermitidaException.class,
                () -> partidoService.finalizar(5L, authentication));
    }
}
