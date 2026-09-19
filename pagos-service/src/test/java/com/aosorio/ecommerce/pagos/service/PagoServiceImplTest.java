package com.aosorio.ecommerce.pagos.service;

import com.aosorio.ecommerce.events.OrderCreatedEvent;
import com.aosorio.ecommerce.pagos.client.PedidoClient;
import com.aosorio.ecommerce.pagos.domain.OutboxEvent;
import com.aosorio.ecommerce.pagos.domain.Pago;
import com.aosorio.ecommerce.pagos.dto.PagoRequestDTO;
import com.aosorio.ecommerce.pagos.dto.PagoResponseDTO;
import com.aosorio.ecommerce.pagos.exception.ResourceInUseException;
import com.aosorio.ecommerce.pagos.exception.ResourceNotFoundException;
import com.aosorio.ecommerce.pagos.mapper.PagoMapper;
import com.aosorio.ecommerce.pagos.repository.OutboxEventRepository;
import com.aosorio.ecommerce.pagos.repository.PagoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PagoServiceImplTest {

    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private PedidoClient pedidoClient;
    @Mock
    private ConfiguracionService configuracionService;

    private PagoMapper pagoMapper;
    private ObjectMapper objectMapper;
    private PagoServiceImpl pagoService;

    @BeforeEach
    void setUp() {
        pagoMapper = new PagoMapper();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        pagoService = new PagoServiceImpl(
                pagoRepository, pagoMapper, outboxEventRepository, objectMapper, pedidoClient, configuracionService);
        lenient().when(configuracionService.montoMaximoAprobado()).thenReturn(new BigDecimal("5000.00"));
    }

    private Pago pago(Long id, Long pedidoId, Long usuarioId, BigDecimal monto, String estado, Integer intento) {
        return Pago.builder()
                .id(id)
                .pedidoId(pedidoId)
                .usuarioId(usuarioId)
                .monto(monto)
                .estado(Pago.EstadoPago.valueOf(estado))
                .motivoRechazo("RECHAZADO".equals(estado)
                        ? "Monto excede el máximo aprobado (5000.00)" : null)
                .intento(intento)
                .fechaProcesado(LocalDateTime.now())
                .build();
    }

    private void stubPedidoSinPagoPrevio(Long pedidoId) {
        when(pagoRepository.existsByPedidoIdAndEstado(pedidoId, Pago.EstadoPago.PROCESADO)).thenReturn(false);
        when(pagoRepository.findFirstByPedidoIdOrderByIdDesc(pedidoId)).thenReturn(Optional.empty());
    }

    @Test
    void procesarPagoBajoElLimiteQuedaProcesadoYPublicaEvento() {
        stubPedidoSinPagoPrevio(5L);
        when(pagoRepository.save(any(Pago.class)))
                .thenReturn(pago(1L, 5L, 9L, new BigDecimal("100.00"), "PROCESADO", 1));

        PagoResponseDTO respuesta = pagoService.procesar(9L,
                PagoRequestDTO.builder().pedidoId(5L).monto(new BigDecimal("100.00")).build());

        verify(pedidoClient).validarPagable(5L, 9L);
        assertThat(respuesta.estado()).isEqualTo("PROCESADO");
        assertThat(respuesta.intento()).isEqualTo(1);
        assertThat(respuesta.motivoRechazo()).isNull();

        ArgumentCaptor<Pago> pagoCaptor = ArgumentCaptor.forClass(Pago.class);
        verify(pagoRepository).save(pagoCaptor.capture());
        assertThat(pagoCaptor.getValue().getIntento()).isEqualTo(1);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getTipoEvento()).isEqualTo("PAYMENT_PROCESSED");
        assertThat(captor.getValue().getEstado()).isEqualTo(OutboxEvent.EstadoOutbox.PENDIENTE);
        assertThat(captor.getValue().getPayload()).contains("PROCESADO");
        assertThat(captor.getValue().getPayload()).contains("\"intento\":1");
    }

    @Test
    void procesarPagoSobreElLimiteSeRechazaConMotivo() {
        stubPedidoSinPagoPrevio(5L);
        when(pagoRepository.save(any(Pago.class)))
                .thenReturn(pago(1L, 5L, 9L, new BigDecimal("9999.00"), "RECHAZADO", 1));

        PagoResponseDTO respuesta = pagoService.procesar(9L,
                PagoRequestDTO.builder().pedidoId(5L).monto(new BigDecimal("9999.00")).build());

        assertThat(respuesta.estado()).isEqualTo("RECHAZADO");
        assertThat(respuesta.intento()).isEqualTo(1);
        assertThat(respuesta.motivoRechazo()).isEqualTo("Monto excede el máximo aprobado (5000.00)");

        ArgumentCaptor<Pago> pagoCaptor = ArgumentCaptor.forClass(Pago.class);
        verify(pagoRepository).save(pagoCaptor.capture());
        assertThat(pagoCaptor.getValue().getEstado()).isEqualTo(Pago.EstadoPago.RECHAZADO);
        assertThat(pagoCaptor.getValue().getMotivoRechazo()).contains("máximo aprobado");

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getPayload()).contains("RECHAZADO");
        assertThat(outboxCaptor.getValue().getPayload()).contains("máximo aprobado");
    }

    @Test
    void reintentoTrasRechazoCreaIntentoSiguiente() {
        when(pagoRepository.existsByPedidoIdAndEstado(5L, Pago.EstadoPago.PROCESADO)).thenReturn(false);
        when(pagoRepository.findFirstByPedidoIdOrderByIdDesc(5L)).thenReturn(
                Optional.of(pago(1L, 5L, 9L, new BigDecimal("9999.00"), "RECHAZADO", 1)));
        when(pagoRepository.save(any(Pago.class)))
                .thenReturn(pago(2L, 5L, 9L, new BigDecimal("120.00"), "PROCESADO", 2));

        PagoResponseDTO respuesta = pagoService.procesar(9L,
                PagoRequestDTO.builder().pedidoId(5L).monto(new BigDecimal("120.00")).build());

        assertThat(respuesta.estado()).isEqualTo("PROCESADO");
        ArgumentCaptor<Pago> captor = ArgumentCaptor.forClass(Pago.class);
        verify(pagoRepository).save(captor.capture());
        assertThat(captor.getValue().getIntento()).isEqualTo(2);
    }

    @Test
    void procesarPagoDePedidoYaPagadoLanzaResourceInUse() {
        when(pagoRepository.existsByPedidoIdAndEstado(5L, Pago.EstadoPago.PROCESADO)).thenReturn(true);

        assertThatThrownBy(() -> pagoService.procesar(9L,
                PagoRequestDTO.builder().pedidoId(5L).monto(new BigDecimal("100.00")).build()))
                .isInstanceOf(ResourceInUseException.class)
                .hasMessageContaining("ya está pagado");

        verify(pagoRepository, never()).save(any());
    }

    @Test
    void procesarBloqueaPedidoNoPagable() {
        doThrow(new ResourceInUseException("El pedido con id 5 no está en estado CREADO (estado actual: CANCELADO)"))
                .when(pedidoClient).validarPagable(5L, 9L);

        assertThatThrownBy(() -> pagoService.procesar(9L,
                PagoRequestDTO.builder().pedidoId(5L).monto(new BigDecimal("100.00")).build()))
                .isInstanceOf(ResourceInUseException.class);

        verify(pagoRepository, never()).save(any());
    }

    @Test
    void procesarDesdeEventoGuardaSiNoExistePagoYSinValidarElPedido() {
        when(pagoRepository.findFirstByPedidoIdOrderByIdDesc(5L)).thenReturn(Optional.empty());
        when(pagoRepository.existsByPedidoIdAndEstado(5L, Pago.EstadoPago.PROCESADO)).thenReturn(false);
        when(pagoRepository.save(any(Pago.class)))
                .thenReturn(pago(2L, 5L, 9L, new BigDecimal("200.00"), "PROCESADO", 1));

        PagoResponseDTO respuesta = pagoService.procesarDesdeEvento(
                new OrderCreatedEvent(5L, 9L, new BigDecimal("200.00"), Instant.now()));

        assertThat(respuesta.estado()).isEqualTo("PROCESADO");
        assertThat(respuesta.intento()).isEqualTo(1);
        assertThat(respuesta.pedidoId()).isEqualTo(5L);
        verify(pedidoClient, never()).validarPagable(any(), any());
    }

    @Test
    void procesarDesdeEventoReutilizaElUltimoIntento() {
        when(pagoRepository.findFirstByPedidoIdOrderByIdDesc(5L))
                .thenReturn(Optional.of(pago(7L, 5L, 9L, new BigDecimal("200.00"), "RECHAZADO", 1)));

        PagoResponseDTO respuesta = pagoService.procesarDesdeEvento(
                new OrderCreatedEvent(5L, 9L, new BigDecimal("200.00"), Instant.now()));

        assertThat(respuesta.id()).isEqualTo(7L);
        verify(pagoRepository, never()).save(any());
    }

    @Test
    void obtenerPorIdInexistenteLanzaNotFound() {
        when(pagoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pagoService.obtenerPorId(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void obtenerPorPedidoSinPagoLanzaNotFound() {
        when(pagoRepository.findFirstByPedidoIdOrderByIdDesc(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pagoService.obtenerPorPedido(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}