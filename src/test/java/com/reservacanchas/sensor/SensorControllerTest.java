package com.reservacanchas.sensor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reservacanchas.controller.SensorController;
import com.reservacanchas.dto.partido.EventoResponse;
import com.reservacanchas.dto.sensor.DeteccionYoloRequest;
import com.reservacanchas.dto.sensor.EventoSensorRequest;
import com.reservacanchas.dto.sensor.LatidoRequest;
import com.reservacanchas.dto.sensor.LatidoResponse;
import com.reservacanchas.exception.CredencialesInvalidasException;
import com.reservacanchas.exception.GlobalExceptionHandler;
import com.reservacanchas.exception.OperacionNoPermitidaException;
import com.reservacanchas.model.enums.EstadoEvento;
import com.reservacanchas.model.enums.LadoEquipo;
import com.reservacanchas.model.enums.OrigenEvento;
import com.reservacanchas.model.enums.TipoEvento;
import com.reservacanchas.service.SensorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SensorControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SensorService sensorService;

    @Mock
    private com.reservacanchas.service.impl.DetectorPorYolo detectorPorYolo;

    @Mock
    private com.reservacanchas.mapper.EventoMapper eventoMapper;

    @InjectMocks
    private SensorController sensorController;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        mockMvc = MockMvcBuilders.standaloneSetup(sensorController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void eventos_credencialesIncorrectas_retorna401() throws Exception {
        when(sensorService.procesarEvento(any(EventoSensorRequest.class)))
                .thenThrow(new CredencialesInvalidasException("Credenciales invalidas del sensor"));

        EventoSensorRequest request = new EventoSensorRequest("SENSOR-01", "clave-falsa", LocalDateTime.now());

        mockMvc.perform(post("/api/sensores/eventos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Credenciales invalidas del sensor"));
    }

    @Test
    void eventos_sinPartidoEnJuego_retorna400ConErrorClaro() throws Exception {
        when(sensorService.procesarEvento(any(EventoSensorRequest.class)))
                .thenThrow(new OperacionNoPermitidaException("No hay ningun partido en juego para esta cancha"));

        EventoSensorRequest request = new EventoSensorRequest("SENSOR-01", "clave-ok", LocalDateTime.now());

        mockMvc.perform(post("/api/sensores/eventos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("No hay ningun partido en juego para esta cancha"));
    }

    @Test
    void eventos_conExito_retorna201YGolPendiente() throws Exception {
        LocalDateTime ahora = LocalDateTime.now();
        EventoResponse response = new EventoResponse(
                1L, TipoEvento.GOL, LadoEquipo.B, ahora, 15, OrigenEvento.SENSOR,
                EstadoEvento.PENDIENTE_ATRIBUCION, "Gol del equipo B", true,
                null, null, null, null, null
        );

        when(sensorService.procesarEvento(any(EventoSensorRequest.class))).thenReturn(response);

        EventoSensorRequest request = new EventoSensorRequest("SENSOR-01", "clave-ok", ahora);

        mockMvc.perform(post("/api/sensores/eventos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.origen").value("SENSOR"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE_ATRIBUCION"))
                .andExpect(jsonPath("$.equipo").value("B"));
    }

    @Test
    void latido_actualizaCorrectamente_retorna200() throws Exception {
        LocalDateTime ahora = LocalDateTime.now();
        LatidoResponse response = new LatidoResponse("SENSOR-01", ahora, true);

        when(sensorService.registrarLatido(any(LatidoRequest.class))).thenReturn(response);

        LatidoRequest request = new LatidoRequest("SENSOR-01", null, "clave-ok");

        mockMvc.perform(post("/api/sensores/latido")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("SENSOR-01"))
                .andExpect(jsonPath("$.enLinea").value(true));
    }

    @Test
    void latido_credencialesInvalidas_retorna401() throws Exception {
        when(sensorService.registrarLatido(any(LatidoRequest.class)))
                .thenThrow(new CredencialesInvalidasException("Credenciales invalidas del sensor"));

        LatidoRequest request = new LatidoRequest("SENSOR-01", null, "clave-falsa");

        mockMvc.perform(post("/api/sensores/latido")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Credenciales invalidas del sensor"));
    }

    @Test
    void yoloDeteccion_sinClaveNiAuth_retorna401() throws Exception {
        when(detectorPorYolo.procesarDeteccionPorCamara(any(), any(), any()))
                .thenThrow(new CredencialesInvalidasException("Se requiere rol de administrador de sede o clave API de cámara válida"));

        DeteccionYoloRequest request = new DeteccionYoloRequest(LadoEquipo.A, LocalDateTime.now(), 0.92, null);

        mockMvc.perform(post("/api/sensores/yolo/camaras/1/deteccion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Se requiere rol de administrador de sede o clave API de cámara válida"));
    }

    @Test
    void yoloDeteccion_usuarioSinPermiso_retorna403() throws Exception {
        when(detectorPorYolo.procesarDeteccionPorPartido(any(), any(), any()))
                .thenThrow(new org.springframework.security.access.AccessDeniedException("No tiene permisos para gestionar la sede de esta detección"));

        DeteccionYoloRequest request = new DeteccionYoloRequest(LadoEquipo.A, LocalDateTime.now(), 0.92, null);

        mockMvc.perform(post("/api/sensores/yolo/partidos/10/deteccion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("No tiene permisos para gestionar la sede de esta detección"));
    }

    @Test
    void yoloDeteccion_conExito_retorna201() throws Exception {
        com.reservacanchas.model.Gol gol = new com.reservacanchas.model.Gol();
        gol.setId(100L);
        gol.setEquipo(LadoEquipo.A);

        EventoResponse response = new EventoResponse(
                100L, TipoEvento.GOL, LadoEquipo.A, LocalDateTime.now(), 10,
                OrigenEvento.YOLO, EstadoEvento.PENDIENTE_ATRIBUCION, "Gol detectado por YOLO",
                true, null, null, null, null, null
        );

        when(detectorPorYolo.procesarDeteccionPorCamara(any(), any(), any())).thenReturn(gol);
        when(eventoMapper.toResponse(gol)).thenReturn(response);

        DeteccionYoloRequest request = new DeteccionYoloRequest(LadoEquipo.A, LocalDateTime.now(), 0.95, "valid-key");

        mockMvc.perform(post("/api/sensores/yolo/camaras/1/deteccion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.origen").value("YOLO"))
                .andExpect(jsonPath("$.equipo").value("A"));
    }
}
