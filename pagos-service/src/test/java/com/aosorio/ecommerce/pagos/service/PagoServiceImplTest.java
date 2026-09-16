package com.aosorio.ecommerce.pagos.service;

import com.aosorio.ecommerce.events.OrderCreatedEvent;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PagoServiceImplTest {

    @Mock
    private PagoRepository pagoRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;

    private PagoMapper pagoMapper;
    private ObjectMapper objectMapper;
    private PagoServiceImpl pagoService;

    @BeforeEach
    void setUp() {
        pagoMapper = new PagoMapper();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        pagoService = new PagoServiceImpl(pagoRepository, pagoMapper, outboxEventRepository, objectMapper);
        ReflectionTestUtils.setField(pagoService, "montoMaximoAprobado", new BigDecimal("5000.00"));
    }

    private Pago pago(Long id, Long pedidoId, Long usuarioId, BigDecimal monto, String estado) {
        return Pago.builder()
                .id(id)
                .pedidoId(pedidoId)
                .usuarioId(usuarioId)
                .monto(monto)
                .estado(Pago.EstadoPago.valueOf(estado))
                .fechaProcesado(LocalDateTime.now())
                .build();
    }

    @Test
    void procesarPagoBajoElLimiteQuedaProcesadoYPublicaEvento() {
        when(pagoRepository.existsByPedidoId(5L)).thenReturn(false);
        when(pagoRepository.save(any(Pago.class))).thenReturn(pago(1L, 5L, 9L, new BigDecimal("100.00"), "PROCESADO"));

        PagoResponseDTO respuesta = pagoService.procesar(9L,
                PagoRequestDTO.builder().pedidoId(5L).monto(new BigDecimal("100.00")).build());

        assertThat(respuesta.estado()).isEqualTo("PROCESADO");
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getTipoEvento()).isEqualTo("PAYMENT_PROCESSED");
        assertThat(captor.getValue().getEstado()).isEqualTo(OutboxEvent.EstadoOutbox.PENDIENTE);
        assertThat(captor.getValue().getPayload()).contains("PROCESADO");
    }

    @Test
    void procesarPagoSobreElLimiteSeRechaza() {
        when(pagoRepository.existsByPedidoId(5L)).thenReturn(false);
        when(pagoRepository.save(any(Pago.class))).thenReturn(pago(1L, 5L, 9L, new BigDecimal("9999.00"), "RECHAZADO"));

        PagoResponseDTO respuesta = pagoService.procesar(9L,
                PagoRequestDTO.builder().pedidoId(5L).monto(new BigDecimal("9999.00")).build());

        assertThat(respuesta.estado()).isEqualTo("RECHAZADO");
    }

    @Test
    void procesarPagoDuplicadoLanzaResourceInUse() {
        when(pagoRepository.existsByPedidoId(5L)).thenReturn(true);

        assertThatThrownBy(() -> pagoService.procesar(9L,
                PagoRequestDTO.builder().pedidoId(5L).monto(new BigDecimal("100.00")).build()))
                .isInstanceOf(ResourceInUseException.class);

        verify(pagoRepository, never()).save(any());
    }

    @Test
    void procesarDesdeEventoGuardaSiNoExistePago() {
        when(pagoRepository.findByPedidoId(5L)).thenReturn(Optional.empty());
        when(pagoRepository.existsByPedidoId(5L)).thenReturn(false);
        when(pagoRepository.save(any(Pago.class)))
                .thenReturn(pago(2L, 5L, 9L, new BigDecimal("200.00"), "PROCESADO"));

        PagoResponseDTO respuesta = pagoService.procesarDesdeEvento(
                new OrderCreatedEvent(5L, 9L, new BigDecimal("200.00"), Instant.now()));

        assertThat(respuesta.estado()).isEqualTo("PROCESADO");
        assertThat(respuesta.pedidoId()).isEqualTo(5L);
    }

    @Test
    void procesarDesdeEventoReutilizaElPagoExistente() {
        when(pagoRepository.findByPedidoId(5L))
                .thenReturn(Optional.of(pago(7L, 5L, 9L, new BigDecimal("200.00"), "RECHAZADO")));

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
        when(pagoRepository.findByPedidoId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pagoService.obtenerPorPedido(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}