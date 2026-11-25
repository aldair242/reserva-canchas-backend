package com.reservacanchas.partido;

import com.reservacanchas.dto.partido.ParticipanteResponse;
import com.reservacanchas.dto.partido.UnirsePartidoRequest;
import com.reservacanchas.exception.OperacionNoPermitidaException;
import com.reservacanchas.model.CanchaFutbol;
import com.reservacanchas.model.Invitado;
import com.reservacanchas.model.Jugador;
import com.reservacanchas.model.ParticipantePartido;
import com.reservacanchas.model.Partido;
import com.reservacanchas.model.Reserva;
import com.reservacanchas.model.enums.EstadoPartido;
import com.reservacanchas.model.enums.LadoEquipo;
import com.reservacanchas.repository.InvitadoRepository;
import com.reservacanchas.repository.ParticipantePartidoRepository;
import com.reservacanchas.repository.PartidoRepository;
import com.reservacanchas.service.SecurityService;
import com.reservacanchas.service.impl.InvitacionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvitacionUnirseTest {

    @Mock
    private PartidoRepository partidoRepository;

    @Mock
    private InvitadoRepository invitadoRepository;

    @Mock
    private ParticipantePartidoRepository participanteRepository;

    @Mock
    private SecurityService securityService;

    @InjectMocks
    private InvitacionServiceImpl invitacionService;

    private Partido partido;
    private CanchaFutbol cancha;
    private Reserva reserva;

    @BeforeEach
    void setUp() {
        cancha = new CanchaFutbol();
        cancha.setId(1L);
        cancha.setFormato(com.reservacanchas.model.enums.FormatoFutbol.FUTBOL_7);

        reserva = new Reserva();
        reserva.setId(10L);
        reserva.setCancha(cancha);

        partido = new Partido();
        partido.setId(100L);
        partido.setTokenInvitacion("INV-TEST-TOKEN");
        partido.setEstado(EstadoPartido.PROGRAMADO);
        partido.setReserva(reserva);
        partido.setParticipantes(new ArrayList<>());
    }

    @Test
    void unirseAPartido_jugadorAutenticado_seUneExitosamente() {
        Jugador jugador = new Jugador();
        jugador.setId(5L);
        jugador.setNombre("Lionel");
        jugador.setApellido("Messi");

        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("lionel@futbol.com");
        when(securityService.getUsuarioActual(auth)).thenReturn(jugador);
        when(partidoRepository.findByTokenInvitacion("INV-TEST-TOKEN")).thenReturn(Optional.of(partido));
        when(partidoRepository.save(any(Partido.class))).thenAnswer(inv -> inv.getArgument(0));

        UnirsePartidoRequest request = new UnirsePartidoRequest(LadoEquipo.A, null, null);

        ParticipanteResponse response = invitacionService.unirseAPartido("INV-TEST-TOKEN", request, auth);

        assertNotNull(response);
        assertEquals(LadoEquipo.A, response.equipo());
        assertEquals(5L, response.personaId());
        assertFalse(response.invitado());
        assertEquals(1, partido.getParticipantes().size());
    }

    @Test
    void unirseAPartido_mismoJugadorIntentaUnirseDosVeces_lanzaExcepcion() {
        Jugador jugador = new Jugador();
        jugador.setId(5L);

        ParticipantePartido pExistente = new ParticipantePartido();
        pExistente.setPersona(jugador);
        pExistente.setEquipo(LadoEquipo.A);
        partido.agregarParticipante(pExistente);

        Authentication auth = mock(Authentication.class);
        org.mockito.Mockito.lenient().when(auth.isAuthenticated()).thenReturn(true);
        org.mockito.Mockito.lenient().when(auth.getPrincipal()).thenReturn("lionel@futbol.com");
        org.mockito.Mockito.lenient().when(securityService.getUsuarioActual(auth)).thenReturn(jugador);
        when(partidoRepository.findByTokenInvitacion("INV-TEST-TOKEN")).thenReturn(Optional.of(partido));

        UnirsePartidoRequest request = new UnirsePartidoRequest(LadoEquipo.A, null, null);

        assertThrows(OperacionNoPermitidaException.class,
                () -> invitacionService.unirseAPartido("INV-TEST-TOKEN", request, auth));
    }

    @Test
    void unirseAPartido_invitadoConMismoNombre_lanzaExcepcionPorDuplicado() {
        Invitado invitadoExistente = new Invitado();
        invitadoExistente.setId(20L);
        invitadoExistente.setNombre("Carlos");
        invitadoExistente.setApellido("Perez");

        ParticipantePartido pExistente = new ParticipantePartido();
        pExistente.setPersona(invitadoExistente);
        pExistente.setEquipo(LadoEquipo.A);
        partido.agregarParticipante(pExistente);

        when(partidoRepository.findByTokenInvitacion("INV-TEST-TOKEN")).thenReturn(Optional.of(partido));

        UnirsePartidoRequest request = new UnirsePartidoRequest(LadoEquipo.A, "Carlos", "Perez");

        assertThrows(OperacionNoPermitidaException.class,
                () -> invitacionService.unirseAPartido("INV-TEST-TOKEN", request, null));
    }

    @Test
    void unirseAPartido_invitadoNuevo_seRegistraYUne() {
        when(partidoRepository.findByTokenInvitacion("INV-TEST-TOKEN")).thenReturn(Optional.of(partido));
        when(invitadoRepository.save(any(Invitado.class))).thenAnswer(inv -> {
            Invitado i = inv.getArgument(0);
            i.setId(30L);
            return i;
        });
        when(partidoRepository.save(any(Partido.class))).thenAnswer(inv -> inv.getArgument(0));

        UnirsePartidoRequest request = new UnirsePartidoRequest(LadoEquipo.B, "Mateo", "Suarez");

        ParticipanteResponse response = invitacionService.unirseAPartido("INV-TEST-TOKEN", request, null);

        assertNotNull(response);
        assertEquals(LadoEquipo.B, response.equipo());
        assertEquals("Mateo Suarez", response.nombreVisible());
        assertTrue(response.invitado());
    }

    @Test
    void unirseAPartido_invitacionVencida_lanzaOperacionNoPermitida() {
        partido.setEstado(EstadoPartido.FINALIZADO);
        when(partidoRepository.findByTokenInvitacion("INV-TEST-TOKEN")).thenReturn(Optional.of(partido));

        UnirsePartidoRequest request = new UnirsePartidoRequest(LadoEquipo.A, "Juan", "Gomez");

        assertThrows(OperacionNoPermitidaException.class,
                () -> invitacionService.unirseAPartido("INV-TEST-TOKEN", request, null));
    }

    @Test
    void obtenerEnlaceReclamo_organizador_obtieneEnlaceExitosamente() {
        Jugador organizador = new Jugador();
        organizador.setId(10L);
        reserva.setOrganizador(organizador);

        Invitado invitado = new Invitado();
        invitado.setId(50L);
        invitado.setTokenReclamo("REC-12345");

        ParticipantePartido participante = new ParticipantePartido();
        participante.setId(200L);
        participante.setPersona(invitado);
        partido.agregarParticipante(participante);

        Authentication auth = mock(Authentication.class);
        when(securityService.getUsuarioActual(auth)).thenReturn(organizador);
        when(securityService.esAdministrador(auth)).thenReturn(false);
        when(partidoRepository.findById(100L)).thenReturn(Optional.of(partido));

        String enlace = invitacionService.obtenerEnlaceReclamo(100L, 200L, auth);

        assertNotNull(enlace);
        assertEquals("/api/invitaciones/REC-12345/reclamar", enlace);
    }

    @Test
    void obtenerEnlaceReclamo_usuarioNoOrganizador_lanzaAccessDenied() {
        Jugador organizador = new Jugador();
        organizador.setId(10L);
        reserva.setOrganizador(organizador);

        Jugador otroUsuario = new Jugador();
        otroUsuario.setId(99L);

        Authentication auth = mock(Authentication.class);
        when(securityService.getUsuarioActual(auth)).thenReturn(otroUsuario);
        when(securityService.esAdministrador(auth)).thenReturn(false);
        when(partidoRepository.findById(100L)).thenReturn(Optional.of(partido));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> invitacionService.obtenerEnlaceReclamo(100L, 200L, auth));
    }
}
