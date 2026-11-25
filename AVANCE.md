# Reporte de Avance y Verificación - Proyecto Reserva Canchas

Fecha de Ejecución: 2026-11-24  
Entorno: Java 21, Spring Boot 3.3.5, PostgreSQL 16 (Docker / Testcontainers)

---

## 1. Resumen General de Resultados

- **Total de pruebas unitarias y de integración ejecutadas:** 160
- **Fallos (Failures):** 0
- **Errores (Errors):** 0
- **Pruebas omitidas (Skipped):** 0
- **Demo Real de Punta a Punta:** Completada con éxito contra la aplicación levantada en `http://localhost:8080`.

---

## 2. Detalle por Punto

### Punto 1: Protección de Endpoints YOLO y Sensores
| Campo | Detalle |
|---|---|
| **Estado** | COMPLETADO |
| **Archivos Modificados** | `com.reservacanchas.dto.sensor.DeteccionYoloRequest`<br>`com.reservacanchas.service.impl.DetectorPorYolo`<br>`com.reservacanchas.controller.SensorController` |
| **Prueba que lo Demuestra** | `com.reservacanchas.sensor.SensorControllerTest`<br>`com.reservacanchas.sensor.DetectorPorYoloTest`<br>`scratch/demo_e2e.ps1` (Sección 5) |
| **Resultado Real** | - Petición anónima rechazada con **401 Unauthorized**.<br>- Jugador sin permisos ni clave de cámara rechazado con **403 Forbidden**.<br>- Administrador de la sede o dispositivo con clave API (`X-API-Key` o en payload) autorizado con **201 Created**. |

---

### Punto 2: Pagos (Saldo Exacto, Anti-Doble Pago, Reembolsos y Reprogramación)
| Campo | Detalle |
|---|---|
| **Estado** | COMPLETADO |
| **Archivos Modificados** | `com.reservacanchas.model.Pago`<br>`com.reservacanchas.model.Reserva`<br>`com.reservacanchas.service.impl.ReservaServiceImpl` |
| **Prueba que lo Demuestra** | `com.reservacanchas.reserva.ReservaPagosTest`<br>`scratch/demo_e2e.ps1` (Sección 3) |
| **Resultado Real** | - Pago con monto diferente al saldo pendiente rechazado con **400 Bad Request** (`El monto a pagar (50) debe ser exactamente igual al saldo pendiente (120.00)`).<br>- Pago exacto procesado y reserva confirmada exitosamente.<br>- Intento de doble pago en reserva pagada rechazado con **400 Bad Request** (`La reserva ya se encuentra pagada`).<br>- Cancelación reembolsa pagos a estado `REEMBOLSADO`.<br>- Reprogramación recalcula precios y ajusta saldos/reembolsos según variación de tarifa. |

---

### Punto 3: Eventos y Partidos (Autorización y Enmascaramiento de Tokens)
| Campo | Detalle |
|---|---|
| **Estado** | COMPLETADO |
| **Archivos Modificados** | `com.reservacanchas.dto.partido.ParticipanteRequest`<br>`com.reservacanchas.dto.partido.PartidoResponse`<br>`com.reservacanchas.service.PartidoService`<br>`com.reservacanchas.service.impl.PartidoServiceImpl`<br>`com.reservacanchas.service.impl.EventoServiceImpl` |
| **Prueba que lo Demuestra** | `com.reservacanchas.partido.PartidosEventosSeguridadTest`<br>`scratch/demo_e2e.ps1` (Sección 4) |
| **Resultado Real** | - Tercero no autorizado consulta partido y recibe `tokenInvitacion: null` (enmascarado).<br>- Organizador y administradores visualizan el token de invitación en claro.<br>- Endpoint `/api/partidos/{id}/participantes` exige `tokenInvitacion` si el usuario no es el organizador (rechazado con **400 Bad Request** si se omite).<br>- Solo usuarios autorizados registran, confirman o descartan eventos. |

---

### Punto 4: Invitaciones (Vencimiento, Revocación, Cupos Concurrentes y Enlace de Reclamo)
| Campo | Detalle |
|---|---|
| **Estado** | COMPLETADO |
| **Archivos Modificados** | `com.reservacanchas.model.Partido`<br>`com.reservacanchas.repository.PartidoRepository`<br>`com.reservacanchas.service.InvitacionService`<br>`com.reservacanchas.service.impl.InvitacionServiceImpl`<br>`com.reservacanchas.controller.InvitacionController`<br>`com.reservacanchas.controller.PartidoController` |
| **Prueba que lo Demuestra** | `com.reservacanchas.partido.InvitacionUnirseTest`<br>`scratch/demo_e2e.ps1` (Sección 4) |
| **Resultado Real** | - Invitaciones vencidas o revocadas son rechazadas.<br>- Consulta con `PESSIMISTIC_WRITE` bloquea el partido durante la inscripción asegurando que no se superen los cupos máximos por equipo.<br>- Organizador obtiene enlace de reclamo del invitado (`/api/invitaciones/{tokenReclamo}/reclamar`).<br>- Tercero no autorizado es rechazado con **403 Forbidden** al intentar obtener el enlace de reclamo. |

---

### Punto 5: Reservas (Expiración de 15 Minutos y Bloqueo de Partidos Cancelados)
| Campo | Detalle |
|---|---|
| **Estado** | COMPLETADO |
| **Archivos Modificados** | `src/main/resources/db/migration/V7__add_fecha_creacion_reservas.sql`<br>`com.reservacanchas.model.Reserva`<br>`com.reservacanchas.service.impl.PartidoCicloVidaService`<br>`com.reservacanchas.service.impl.PartidoServiceImpl` |
| **Prueba que lo Demuestra** | `com.reservacanchas.service.PartidoCicloVidaYParticipantesTest` |
| **Resultado Real** | - Reservas en estado `PENDIENTE` con más de 15 minutos son canceladas automáticamente por la tarea programada (`procesarCicloDeVida`).<br>- Partidos asociados a reservas canceladas no pueden ser iniciados (`No se puede iniciar un partido cuya reserva ha sido cancelada`). |

---

### Punto 6: YOLO (Deduplicación, Commit Listener, MP4 Reproducible y Compatibilidad BD)
| Campo | Detalle |
|---|---|
| **Estado** | COMPLETADO |
| **Archivos Modificados** | `com.reservacanchas.service.impl.DetectorPorYolo`<br>`com.reservacanchas.event.GolDetectadoEvent`<br>`com.reservacanchas.event.GolEventListener`<br>`com.reservacanchas.service.impl.GeneradorClipFFmpeg`<br>`src/main/resources/db/migration/V5__create_partidos_eventos.sql` |
| **Prueba que lo Demuestra** | `com.reservacanchas.sensor.GeneradorClipFFmpegTest`<br>`com.reservacanchas.sensor.DetectorPorYoloTest`<br>`scratch/demo_e2e.ps1` (Secciones 5 y 6) |
| **Resultado Real** | - Detecciones duplicadas en ventana de 10 segundos rechazadas con **400 Bad Request** (`Detección duplicada: ya existe un gol registrado para el equipo en este lapso temporal`).<br>- Evento de gol procesado tras el commit transaccional mediante `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`.<br>- Video MP4 generado en modo simulado contiene encabezado binario ISO válido (`ftyp`) reproducible de 156 bytes.<br>- Base de datos almacena sin errores `origen = 'YOLO'` (`VARCHAR(20)`). |

---

### Punto 7: Secretos y Logs
| Campo | Detalle |
|---|---|
| **Estado** | COMPLETADO |
| **Archivos Modificados** | `com.reservacanchas.config.DevDataInitializer`<br>`com.reservacanchas.service.impl.AlmacenamientoLocal`<br>`com.reservacanchas.security.JwtService`<br>`src/main/resources/application-dev.yml` |
| **Prueba que lo Demuestra** | `com.reservacanchas.integration.AuthIntegrationTest`<br>`com.reservacanchas.sensor.ClipControllerSecurityTest`<br>`scratch/demo_e2e.ps1` (Inspección de logs) |
| **Resultado Real** | - Se removieron contraseñas por defecto hardcodeadas en código y YAML.<br>- Se eliminó clave de firma por defecto (`reserva-canchas-secreto-firma-clips-2026`), generándose un UUID criptográfico dinámico en memoria si no se provee por variable de entorno.<br>- Logs de inicialización ya no imprimen contraseñas (`log.info("Usuario ... de prueba inicializado: {}", correo)`). |

---

### Punto 8: Verificación Completa con Docker y Demo Real de Punta a Punta
| Campo | Detalle |
|---|---|
| **Estado** | COMPLETADO |
| **Archivos Modificados** | `scratch/demo_e2e.ps1` |
| **Prueba que lo Demuestra** | `.\mvnw.cmd clean verify`<br>`powershell -ExecutionPolicy Bypass -File scratch/demo_e2e.ps1` |
| **Resultado Real** | - `mvn clean verify` finalizó con **BUILD SUCCESS** ejecutando 160 pruebas.<br>- Demo real contra el servidor Tomcat en puerto 8080 con PostgreSQL activo ejecutó exitosamente el ciclo completo de negocio: login, sede, cancha, reserva, pagos exactos y duplicados, creación de partido, seguridad de token, invitaciones, enlace de reclamo, YOLO, deduplicación y descarga de clip MP4 con firma HMAC. |

---

## 3. Pruebas Omitidas

- **Pruebas omitidas:** 0 (Ninguna prueba fue omitida durante la ejecución de la suite).

---

## 4. Requerimientos de Integración Frontend Sincronizados

| Requerimiento Frontend | Estado Backend | Contrato OpenAPI (`docs/openapi.json`) | Implementación |
|---|:---:|:---:|---|
| **Expiración y Cuenta Regresiva de Pago** | **COMPLETADO** | Expuesto en `ReservaResponse`: `fechaLimitePago` (`LocalDateTime`), `saldoPendiente` (`BigDecimal`), `montoPagado` (`BigDecimal`). | Mapeado desde `Reserva.getFechaLimitePago()`, computando exactamente la marca temporal de los 15 minutos máximos permitidos en reservas `PENDIENTE`. |
| **Saldos y Montos Reales de Reserva** | **COMPLETADO** | Expuesto en `ReservaResponse`: `precioTotal`, `saldoPendiente`, `montoPagado`, `pagada`. | Calculados con la lógica de negocio y métodos de pago registrados sin requerir estimaciones en el frontend. |
| **Consulta Global de Partidos para Administradores** | **COMPLETADO** | Nuevo endpoint `GET /api/partidos` con parámetros opcionales `sedeId` y `fecha`. | Protegido con rol `ADMIN` y `ADMIN_SEDE`. El Administrador de Sede consulta los partidos de su sede, y el Administrador General puede filtrar por cualquier sede o fecha. |
| **Endpoints de Administradores de Sede** | **COMPLETADO** | Confirmado en OpenAPI: `GET /api/admin/usuarios/administradores-sede`, `POST`, `PUT /{id}`, `DELETE /{id}`. | Operativos para el rol Administrador General. |
| **Simulación y Streaming YOLO Seguro** | **COMPLETADO** | `POST /api/sensores/yolo/camaras/{camaraId}/deteccion`<br>`POST /api/sensores/yolo/partidos/{partidoId}/deteccion` | Protegidos: rechazan anónimos (401), claves inválidas (401/403) y jugadores sin permisos (403). Aceptan tanto la clave API (`X-API-Key` o payload) como autenticación JWT de Administrador. |

