package com.reservacanchas.controller;

import com.reservacanchas.dto.partido.EventoResponse;
import com.reservacanchas.dto.sensor.DeteccionYoloRequest;
import com.reservacanchas.dto.sensor.EventoSensorRequest;
import com.reservacanchas.dto.sensor.LatidoRequest;
import com.reservacanchas.dto.sensor.LatidoResponse;
import com.reservacanchas.mapper.EventoMapper;
import com.reservacanchas.model.Gol;
import com.reservacanchas.service.SensorService;
import com.reservacanchas.service.impl.DetectorPorYolo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sensores")
@Tag(name = "Sensores", description = "Endpoints de ingesta de telemetria y eventos desde dispositivos IoT (sensores y camaras)")
public class SensorController {

    private final SensorService sensorService;
    private final DetectorPorYolo detectorPorYolo;
    private final EventoMapper eventoMapper;

    public SensorController(SensorService sensorService,
                            DetectorPorYolo detectorPorYolo,
                            EventoMapper eventoMapper) {
        this.sensorService = sensorService;
        this.detectorPorYolo = detectorPorYolo;
        this.eventoMapper = eventoMapper;
    }

    @PostMapping("/eventos")
    @Operation(summary = "Registrar evento de gol desde un sensor de arco", description = "Valida las credenciales de sensor (codigoSensor y claveApi), detecta el partido EN_JUEGO en esa cancha, registra el Gol en estado PENDIENTE_ATRIBUCION y dispara de forma asincrona la generacion del clip de video.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Evento de gol registrado y generacion de clip encolada"),
            @ApiResponse(responseCode = "400", description = "No hay partido en juego en la cancha del sensor"),
            @ApiResponse(responseCode = "401", description = "Credenciales invalidas del sensor o sensor inactivo")
    })
    public ResponseEntity<EventoResponse> registrarEvento(@Valid @RequestBody EventoSensorRequest request) {
        EventoResponse response = sensorService.procesarEvento(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/latido")
    @Operation(summary = "Registrar latido de vida de un dispositivo", description = "Actualiza la marca temporal de ultimo latido del dispositivo para monitorear su estado en linea. Requiere codigoSensor y claveApi.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Latido registrado exitosamente"),
            @ApiResponse(responseCode = "401", description = "Credenciales invalidas del sensor o dispositivo inactivo")
    })
    public ResponseEntity<LatidoResponse> registrarLatido(@RequestBody LatidoRequest request) {
        LatidoResponse response = sensorService.registrarLatido(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/yolo/camaras/{camaraId}/deteccion")
    @Operation(summary = "Ingestar o simular detección de gol mediante YOLO desde una cámara de cancha",
            description = "Recibe el resultado de inferencia del modelo YOLO (o una simulación), valida que el partido esté EN_JUEGO, registra el Gol como PENDIENTE_ATRIBUCION (origen YOLO) y dispara la creación asíncrona del Clip.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Detección procesada y generación de clip encolada"),
            @ApiResponse(responseCode = "400", description = "Cámara inactiva, sin partido en juego o confianza insuficiente"),
            @ApiResponse(responseCode = "401", description = "No autenticado o clave API inválida"),
            @ApiResponse(responseCode = "403", description = "No autorizado para gestionar la cámara"),
            @ApiResponse(responseCode = "404", description = "Cámara no encontrada")
    })
    public ResponseEntity<EventoResponse> procesarDeteccionCamara(
            @PathVariable Long camaraId,
            @Valid @RequestBody DeteccionYoloRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            org.springframework.security.core.Authentication authentication) {
        DeteccionYoloRequest reqEfectivo = resolverRequestConHeader(request, apiKeyHeader);
        Gol gol = detectorPorYolo.procesarDeteccionPorCamara(camaraId, reqEfectivo, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(eventoMapper.toResponse(gol));
    }

    @PostMapping("/yolo/partidos/{partidoId}/deteccion")
    @Operation(summary = "Simular detección de gol por YOLO directamente en un partido en juego",
            description = "Permite probar y simular la detección inteligente en un partido en curso generando el evento de gol y el correspondiente clip de video.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Detección procesada y clip encolado"),
            @ApiResponse(responseCode = "400", description = "Partido no está EN_JUEGO o confianza inferior al umbral"),
            @ApiResponse(responseCode = "401", description = "No autenticado o credenciales inválidas"),
            @ApiResponse(responseCode = "403", description = "No autorizado para gestionar la sede de este partido"),
            @ApiResponse(responseCode = "404", description = "Partido no encontrado")
    })
    public ResponseEntity<EventoResponse> procesarDeteccionPartido(
            @PathVariable Long partidoId,
            @Valid @RequestBody DeteccionYoloRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(value = "X-API-Key", required = false) String apiKeyHeader,
            org.springframework.security.core.Authentication authentication) {
        DeteccionYoloRequest reqEfectivo = resolverRequestConHeader(request, apiKeyHeader);
        Gol gol = detectorPorYolo.procesarDeteccionPorPartido(partidoId, reqEfectivo, authentication);
        return ResponseEntity.status(HttpStatus.CREATED).body(eventoMapper.toResponse(gol));
    }

    private DeteccionYoloRequest resolverRequestConHeader(DeteccionYoloRequest request, String apiKeyHeader) {
        if (request == null) {
            return (apiKeyHeader != null && !apiKeyHeader.isBlank())
                    ? new DeteccionYoloRequest(null, null, null, apiKeyHeader)
                    : null;
        }
        if ((request.claveApi() == null || request.claveApi().isBlank())
                && apiKeyHeader != null && !apiKeyHeader.isBlank()) {
            return new DeteccionYoloRequest(request.equipo(), request.instante(), request.confianza(), apiKeyHeader);
        }
        return request;
    }
}

