package com.reservacanchas.service.impl;

import com.reservacanchas.dto.partido.InvitacionResponse;
import com.reservacanchas.dto.partido.ParticipanteResponse;
import com.reservacanchas.dto.partido.UnirsePartidoRequest;
import com.reservacanchas.exception.OperacionNoPermitidaException;
import com.reservacanchas.exception.ResourceNotFoundException;
import com.reservacanchas.model.Invitado;
import com.reservacanchas.model.Jugador;
import com.reservacanchas.model.ParticipantePartido;
import com.reservacanchas.model.Partido;
import com.reservacanchas.model.Persona;
import com.reservacanchas.model.Usuario;
import com.reservacanchas.model.enums.EstadoPartido;
import com.reservacanchas.repository.InvitadoRepository;
import com.reservacanchas.repository.ParticipantePartidoRepository;
import com.reservacanchas.repository.PartidoRepository;
import com.reservacanchas.service.InvitacionService;
import com.reservacanchas.service.SecurityService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class InvitacionServiceImpl implements InvitacionService {

    private final PartidoRepository partidoRepository;
    private final InvitadoRepository invitadoRepository;
    private final ParticipantePartidoRepository participanteRepository;
    private final SecurityService securityService;
    private final java.time.Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public InvitacionServiceImpl(PartidoRepository partidoRepository,
                                 InvitadoRepository invitadoRepository,
                                 ParticipantePartidoRepository participanteRepository,
                                 SecurityService securityService,
                                 java.time.Clock clock) {
        this.partidoRepository = partidoRepository;
        this.invitadoRepository = invitadoRepository;
        this.participanteRepository = participanteRepository;
        this.securityService = securityService;
        this.clock = clock != null ? clock : java.time.Clock.system(com.reservacanchas.config.ClockConfig.ZONA_HORARIA);
    }

    public InvitacionServiceImpl(PartidoRepository partidoRepository,
                                 InvitadoRepository invitadoRepository,
                                 ParticipantePartidoRepository participanteRepository,
                                 SecurityService securityService) {
        this(partidoRepository, invitadoRepository, participanteRepository, securityService, java.time.Clock.system(com.reservacanchas.config.ClockConfig.ZONA_HORARIA));
    }

    @Override
    @Transactional(readOnly = true)
    public InvitacionResponse obtenerResumen(String token) {
        Partido partido = partidoRepository.findByTokenInvitacion(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invitacion", token));

        if (partido.esInvitacionVencida(java.time.LocalDateTime.now(clock))) {
            throw new OperacionNoPermitidaException("La invitación ha vencido o el partido ya no está en estado programado");
        }

        List<ParticipanteResponse> participantes = partido.getParticipantes().stream()
                .map(p -> new ParticipanteResponse(
                        p.getId(),
                        p.getEquipo(),
                        p.isAsistio(),
                        p.getPersona() != null ? p.getPersona().getId() : null,
                        p.getPersona() != null ? p.getPersona().getNombreCompleto() : "Invitado",
                        p.getPersona() instanceof Invitado
                ))
                .toList();

        return new InvitacionResponse(
                partido.getId(),
                partido.getEstado(),
                partido.getNombreEquipoA(),
                partido.getNombreEquipoB(),
                partido.getGolesEquipoA(),
                partido.getGolesEquipoB(),
                partido.getReserva().getFecha(),
                partido.getReserva().getHoraInicio(),
                partido.getReserva().getHoraFin(),
                partido.getReserva().getCancha().getNombre(),
                partido.getReserva().getCancha().getSede().getNombre(),
                participantes
        );
    }

    @Override
    @Transactional
    public ParticipanteResponse unirseAPartido(String token, UnirsePartidoRequest request, Authentication authentication) {
        if (request == null || request.equipo() == null) {
            throw new IllegalArgumentException("Debe especificar el equipo al que desea unirse");
        }

        Partido partido = partidoRepository.findByTokenInvitacionForUpdate(token)
                .or(() -> partidoRepository.findByTokenInvitacion(token))
                .orElseThrow(() -> new ResourceNotFoundException("Invitacion", token));

        if (partido.esInvitacionVencida(java.time.LocalDateTime.now(clock))) {
            throw new OperacionNoPermitidaException("La invitación ha vencido o el partido ya no está en estado programado");
        }

        if (partido.getEstado() != EstadoPartido.PROGRAMADO) {
            throw new OperacionNoPermitidaException("No es posible unirse a un partido en estado " + partido.getEstado());
        }

        int capacidadMaxima = (partido.getReserva() != null && partido.getReserva().getCancha() != null
                && partido.getReserva().getCancha().getCapacidadJugadores() > 0)
                ? partido.getReserva().getCancha().getCapacidadJugadores() : 14;

        if (partido.getParticipantes().size() >= capacidadMaxima) {
            throw new OperacionNoPermitidaException("La capacidad máxima de jugadores para esta cancha (" + capacidadMaxima + ") ya ha sido alcanzada");
        }

        long cuposEquipo = partido.getParticipantes().stream()
                .filter(p -> p.getEquipo() == request.equipo())
                .count();
        int maxPorEquipo = (capacidadMaxima + 1) / 2;
        if (cuposEquipo >= maxPorEquipo) {
            throw new OperacionNoPermitidaException("El equipo " + request.equipo() + " ya tiene cubierto su cupo maximo de jugadores");
        }

        Persona persona;
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            Usuario usuarioActual = securityService.getUsuarioActual(authentication);
            if (!(usuarioActual instanceof Jugador jugador)) {
                throw new OperacionNoPermitidaException("Solo los usuarios con rol Jugador pueden unirse al partido");
            }
            persona = jugador;
        } else {
            String nombre = request.nombre() != null ? request.nombre().trim() : "";
            String apellido = request.apellido() != null ? request.apellido().trim() : "";
            if (nombre.isBlank() || apellido.isBlank()) {
                throw new IllegalArgumentException("Debe proporcionar nombre y apellido para unirse como invitado");
            }

            // Validar que no exista ya un participante con ese mismo nombre y apellido en el partido
            boolean nombreDuplicado = partido.getParticipantes().stream()
                    .anyMatch(p -> p.getPersona() != null
                            && nombre.equalsIgnoreCase(p.getPersona().getNombre())
                            && apellido.equalsIgnoreCase(p.getPersona().getApellido()));
            if (nombreDuplicado) {
                throw new OperacionNoPermitidaException("Ya existe una persona registrada con el nombre " + nombre + " " + apellido + " en este partido");
            }

            Invitado nuevoInvitado = new Invitado();
            nuevoInvitado.setNombre(nombre);
            nuevoInvitado.setApellido(apellido);
            nuevoInvitado.setReclamado(false);
            nuevoInvitado.setTokenReclamo("REC-" + UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 20).toUpperCase());
            persona = invitadoRepository.save(nuevoInvitado);
        }

        // Validar que la persona no esté ya inscrita por id en el partido
        boolean yaInscrito = partido.getParticipantes().stream()
                .anyMatch(p -> p.getPersona() != null && p.getPersona().getId().equals(persona.getId()));
        if (yaInscrito) {
            throw new OperacionNoPermitidaException("Ya te encuentras registrado como participante en este partido");
        }

        ParticipantePartido participante = new ParticipantePartido();
        participante.setPersona(persona);
        participante.setEquipo(request.equipo());
        participante.setAsistio(false);
        partido.agregarParticipante(participante);

        Partido guardado = partidoRepository.save(partido);

        ParticipantePartido persistido = guardado.getParticipantes().stream()
                .filter(p -> p.getPersona() != null && p.getPersona().getId().equals(persona.getId()))
                .findFirst()
                .orElse(participante);

        return new ParticipanteResponse(
                persistido.getId(),
                persistido.getEquipo(),
                persistido.isAsistio(),
                persona.getId(),
                persona.getNombreCompleto(),
                persona instanceof Invitado
        );
    }

    @Override
    @Transactional
    public void reclamarPerfil(String token, Authentication authentication) {
        Usuario usuario = securityService.getUsuarioActual(authentication);
        if (!(usuario instanceof Jugador jugador)) {
            throw new OperacionNoPermitidaException("Solo los jugadores pueden reclamar un perfil");
        }

        Invitado invitado = invitadoRepository.findByTokenReclamo(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invitado", token));

        if (invitado.isReclamado()) {
            throw new OperacionNoPermitidaException("Este invitado ya fue reclamado");
        }

        // Reemplazar la referencia al Invitado por el Jugador en todas las participaciones
        participanteRepository.reemplazarPersona(invitado, jugador);

        invitado.reclamarPerfil(jugador);
        invitadoRepository.save(invitado);
    }

    @Override
    @Transactional(readOnly = true)
    public String obtenerEnlaceReclamo(Long partidoId, Long participanteId, Authentication authentication) {
        Usuario usuario = securityService.getUsuarioActual(authentication);
        Partido partido = partidoRepository.findById(partidoId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido", partidoId));

        boolean esAdmin = securityService.esAdministrador(authentication);
        boolean esOrganizador = partido.getReserva() != null && partido.getReserva().getOrganizador() != null
                && partido.getReserva().getOrganizador().getId().equals(usuario.getId());

        if (!esAdmin && !esOrganizador) {
            throw new org.springframework.security.access.AccessDeniedException("Solo el organizador del partido puede obtener el enlace de reclamo");
        }

        ParticipantePartido participante = partido.getParticipantes().stream()
                .filter(p -> p.getId().equals(participanteId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Participante", participanteId));

        Invitado invitado = null;
        if (participante.getPersona() instanceof Invitado inv) {
            invitado = inv;
        } else if (participante.getPersona() != null) {
            invitado = invitadoRepository.findById(participante.getPersona().getId()).orElse(null);
        }

        if (invitado == null || invitado.isReclamado()) {
            throw new OperacionNoPermitidaException("El participante no es un invitado o ya ha sido reclamado");
        }

        return "/api/invitaciones/" + invitado.getTokenReclamo() + "/reclamar";
    }
}
