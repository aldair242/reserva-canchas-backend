package com.reservacanchas.reserva;

import com.reservacanchas.config.ClockConfig;
import com.reservacanchas.dto.reserva.CancelarRequest;
import com.reservacanchas.dto.reserva.PagoRequest;
import com.reservacanchas.dto.reserva.ReprogramarRequest;
import com.reservacanchas.dto.reserva.ReservaResponse;
import com.reservacanchas.exception.OperacionNoPermitidaException;
import com.reservacanchas.mapper.ReservaMapper;
import com.reservacanchas.model.CanchaFutbol;
import com.reservacanchas.model.Jugador;
import com.reservacanchas.model.Pago;
import com.reservacanchas.model.PagoTarjeta;
import com.reservacanchas.model.Reserva;
import com.reservacanchas.model.Sede;
import com.reservacanchas.model.enums.EstadoPago;
import com.reservacanchas.model.enums.EstadoReserva;
import com.reservacanchas.model.enums.TipoPago;
import com.reservacanchas.repository.CanchaRepository;
import com.reservacanchas.repository.PartidoRepository;
import com.reservacanchas.repository.ReservaRepository;
import com.reservacanchas.service.PagoService;
import com.reservacanchas.service.SecurityService;
import com.reservacanchas.service.impl.ReservaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservaPagosTest {

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private CanchaRepository canchaRepository;

    @Mock
    private PartidoRepository partidoRepository;

    @Mock
    private PagoService pagoService;

    @Mock
    private ReservaMapper reservaMapper;

    @Mock
    private SecurityService securityService;

    @Mock
    private Authentication authentication;

    private Clock clock;
    private ReservaServiceImpl reservaService;

    private Jugador jugador;
    private Reserva reserva;
    private CanchaFutbol cancha;
    private Sede sede;

    @BeforeEach
    void setUp() {
        clock = Clock.system(ClockConfig.ZONA_HORARIA);
        reservaService = new ReservaServiceImpl(
                reservaRepository,
                canchaRepository,
                partidoRepository,
                reservaMapper,
                securityService,
                pagoService,
                clock
        );

        jugador = new Jugador();
        jugador.setId(1L);

        sede = new Sede();
        sede.setHoraApertura(LocalTime.of(8, 0));
        sede.setHoraCierre(LocalTime.of(23, 0));

        cancha = new CanchaFutbol();
        cancha.setId(10L);
        cancha.setSede(sede);
        cancha.setPrecioHora(BigDecimal.valueOf(100));

        reserva = new Reserva();
        reserva.setId(5L);
        reserva.setOrganizador(jugador);
        reserva.setCancha(cancha);
        reserva.setFecha(LocalDate.now(clock).plusDays(2));
        reserva.setHoraInicio(LocalTime.of(18, 0));
        reserva.setHoraFin(LocalTime.of(19, 0));
        reserva.setPrecioTotal(BigDecimal.valueOf(100.00));
        reserva.setEstado(EstadoReserva.PENDIENTE);
    }

    @Test
    void pagar_montoDistintoAlSaldo_lanzaIllegalArgumentException() {
        when(securityService.getUsuarioActual(authentication)).thenReturn(jugador);
        when(reservaRepository.findById(5L)).thenReturn(Optional.of(reserva));

        PagoRequest request = new PagoRequest(TipoPago.TARJETA, BigDecimal.valueOf(50.00), "ref1", "1234", "VISA", null, null, null, null, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> reservaService.pagar(5L, request, authentication));
        assertEquals("El monto a pagar (50.0) debe ser exactamente igual al saldo pendiente (100.0)", ex.getMessage());
    }

    @Test
    void pagar_reservaYaPagada_lanzaOperacionNoPermitidaException() {
        Pago pago = new PagoTarjeta();
        pago.setMonto(BigDecimal.valueOf(100.00));
        pago.setEstado(EstadoPago.PAGADO);
        reserva.agregarPago(pago);
        reserva.setEstado(EstadoReserva.CONFIRMADA);

        when(securityService.getUsuarioActual(authentication)).thenReturn(jugador);
        when(reservaRepository.findById(5L)).thenReturn(Optional.of(reserva));

        PagoRequest request = new PagoRequest(TipoPago.TARJETA, BigDecimal.valueOf(100.00), "ref1", "1234", "VISA", null, null, null, null, null);

        assertThrows(OperacionNoPermitidaException.class,
                () -> reservaService.pagar(5L, request, authentication));
    }

    @Test
    void cancelar_conPagosRealizados_reembolsaPagos() {
        Pago pago = new PagoTarjeta();
        pago.setMonto(BigDecimal.valueOf(100.00));
        pago.setEstado(EstadoPago.PAGADO);
        reserva.agregarPago(pago);
        reserva.setEstado(EstadoReserva.CONFIRMADA);

        when(securityService.getUsuarioActual(authentication)).thenReturn(jugador);
        when(reservaRepository.findById(5L)).thenReturn(Optional.of(reserva));
        when(partidoRepository.findByReservaId(5L)).thenReturn(Optional.empty());
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));

        reservaService.cancelar(5L, new CancelarRequest("Motivo personal"), authentication);

        assertEquals(EstadoReserva.CANCELADA, reserva.getEstado());
        assertEquals(EstadoPago.REEMBOLSADO, pago.getEstado());
    }

    @Test
    void reprogramar_precioMayor_cambiaEstadoAPendiente() {
        Pago pago = new PagoTarjeta();
        pago.setMonto(BigDecimal.valueOf(100.00));
        pago.setEstado(EstadoPago.PAGADO);
        reserva.agregarPago(pago);
        reserva.setEstado(EstadoReserva.CONFIRMADA);

        when(securityService.getUsuarioActual(authentication)).thenReturn(jugador);
        when(reservaRepository.findById(5L)).thenReturn(Optional.of(reserva));
        when(partidoRepository.findByReservaId(5L)).thenReturn(Optional.empty());
        when(canchaRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(cancha));
        when(reservaRepository.buscarSolapadasExcluyendo(any(), any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(reservaRepository.saveAndFlush(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));

        // 2 horas = 200 > 100 pagado
        ReprogramarRequest request = new ReprogramarRequest(
                LocalDate.now(clock).plusDays(3),
                LocalTime.of(18, 0),
                LocalTime.of(20, 0)
        );

        reservaService.reprogramar(5L, request, authentication);

        assertEquals(EstadoReserva.PENDIENTE, reserva.getEstado());
        assertEquals(0, reserva.getPrecioTotal().compareTo(BigDecimal.valueOf(200)));
        assertEquals(0, reserva.getSaldoPendiente().compareTo(BigDecimal.valueOf(100)));
    }
}
