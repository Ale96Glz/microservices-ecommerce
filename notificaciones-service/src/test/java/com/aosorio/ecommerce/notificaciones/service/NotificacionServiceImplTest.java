package com.aosorio.ecommerce.notificaciones.service;

import com.aosorio.ecommerce.events.OrderCreatedEvent;
import com.aosorio.ecommerce.events.PaymentProcessedEvent;
import com.aosorio.ecommerce.notificaciones.domain.Notificacion;
import com.aosorio.ecommerce.notificaciones.dto.NotificacionRequestDTO;
import com.aosorio.ecommerce.notificaciones.dto.NotificacionResponseDTO;
import com.aosorio.ecommerce.notificaciones.exception.InvalidRequestException;
import com.aosorio.ecommerce.notificaciones.exception.ResourceNotFoundException;
import com.aosorio.ecommerce.notificaciones.mapper.NotificacionMapper;
import com.aosorio.ecommerce.notificaciones.repository.NotificacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacionServiceImplTest {

    @Mock
    private NotificacionRepository notificacionRepository;

    private NotificacionMapper notificacionMapper;
    private NotificacionServiceImpl notificacionService;

    @BeforeEach
    void setUp() {
        notificacionMapper = new NotificacionMapper();
        notificacionService = new NotificacionServiceImpl(notificacionRepository, notificacionMapper);
    }

    private Notificacion notificacion(Long id, Long usuarioId, String tipo, boolean leida) {
        return Notificacion.builder()
                .id(id)
                .usuarioId(usuarioId)
                .tipo(Notificacion.TipoNotificacion.valueOf(tipo))
                .mensaje("Mensaje de prueba")
                .referenciaId(5L)
                .leida(leida)
                .build();
    }

    @Test
    void crearConTipoValidoGuardaLaNotificacion() {
        when(notificacionRepository.save(any(Notificacion.class)))
                .thenAnswer(invocation -> notificacion(1L, 9L, "PAGO_PROCESADO", false));

        NotificacionRequestDTO request = NotificacionRequestDTO.builder()
                .tipo("pago_procesado")
                .mensaje("Tu pago fue aprobado")
                .referenciaId(5L)
                .build();

        NotificacionResponseDTO respuesta = notificacionService.crear(9L, request);

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepository).save(captor.capture());
        assertThat(captor.getValue().getTipo()).isEqualTo(Notificacion.TipoNotificacion.PAGO_PROCESADO);
        assertThat(captor.getValue().getUsuarioId()).isEqualTo(9L);
        assertThat(captor.getValue().isLeida()).isFalse();
        assertThat(respuesta.id()).isEqualTo(1L);
    }

    @Test
    void crearConTipoInvalidoLanzaInvalidRequest() {
        NotificacionRequestDTO request = NotificacionRequestDTO.builder()
                .tipo("NO_EXISTE")
                .mensaje("mensaje")
                .referenciaId(1L)
                .build();

        assertThatThrownBy(() -> notificacionService.crear(9L, request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("NO_EXISTE");
    }

    @Test
    void registrarPedidoCreadoConstruyeElMensajeCorrecto() {
        when(notificacionRepository.save(any(Notificacion.class)))
                .thenAnswer(invocation -> notificacion(2L, 9L, "PEDIDO_CREADO", false));

        notificacionService.registrarPedidoCreado(
                new OrderCreatedEvent(10L, 9L, new BigDecimal("250.50"), Instant.now()));

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepository).save(captor.capture());
        assertThat(captor.getValue().getMensaje()).contains("pedido #10");
        assertThat(captor.getValue().getMensaje()).contains("250.50");
        assertThat(captor.getValue().getReferenciaId()).isEqualTo(10L);
    }

    @Test
    void registrarPagoProcesadoReflejaElEstado() {
        when(notificacionRepository.save(any(Notificacion.class)))
                .thenAnswer(invocation -> notificacion(3L, 9L, "PAGO_PROCESADO", false));

        notificacionService.registrarPagoProcesado(
                new PaymentProcessedEvent(20L, 10L, 9L, new BigDecimal("100.00"), "PROCESADO", null, 1, Instant.now()));

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepository).save(captor.capture());
        assertThat(captor.getValue().getMensaje()).contains("pago #20");
        assertThat(captor.getValue().getMensaje()).contains("PROCESADO");
        assertThat(captor.getValue().getMensaje()).doesNotContain("Motivo:");
        assertThat(captor.getValue().getReferenciaId()).isEqualTo(20L);
    }

    @Test
    void registrarPagoRechazadoIncluyeElMotivo() {
        when(notificacionRepository.save(any(Notificacion.class)))
                .thenAnswer(invocation -> notificacion(4L, 9L, "PAGO_PROCESADO", false));

        notificacionService.registrarPagoProcesado(new PaymentProcessedEvent(21L, 10L, 9L,
                new BigDecimal("9999.00"), "RECHAZADO", "Monto excede el máximo aprobado (5000.00)", 1, Instant.now()));

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepository).save(captor.capture());
        assertThat(captor.getValue().getMensaje()).contains("RECHAZADO");
        assertThat(captor.getValue().getMensaje()).contains("máximo aprobado");
    }

    @Test
    void marcarComoLeidaActualizaElFlag() {
        when(notificacionRepository.findById(5L)).thenReturn(Optional.of(notificacion(5L, 9L, "PEDIDO_CREADO", false)));
        when(notificacionRepository.save(any(Notificacion.class)))
                .thenAnswer(invocation -> notificacion(5L, 9L, "PEDIDO_CREADO", true));

        NotificacionResponseDTO respuesta = notificacionService.marcarComoLeida(5L);

        assertThat(respuesta.leida()).isTrue();
    }

    @Test
    void obtenerPorIdInexistenteLanzaNotFound() {
        when(notificacionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificacionService.obtenerPorId(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void obtenerNoLeidasDelegaAlRepositorio() {
        when(notificacionRepository.findByUsuarioIdAndLeida(9L, false))
                .thenReturn(List.of(notificacion(1L, 9L, "PEDIDO_CREADO", false)));

        List<NotificacionResponseDTO> respuesta = notificacionService.obtenerNoLeidasPorUsuario(9L);

        assertThat(respuesta).hasSize(1);
        assertThat(respuesta.get(0).leida()).isFalse();
    }
}