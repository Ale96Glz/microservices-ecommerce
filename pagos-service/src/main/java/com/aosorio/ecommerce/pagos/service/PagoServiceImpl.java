package com.aosorio.ecommerce.pagos.service;

import com.aosorio.ecommerce.events.OrderCreatedEvent;
import com.aosorio.ecommerce.events.PaymentProcessedEvent;
import com.aosorio.ecommerce.pagos.client.PedidoClient;
import com.aosorio.ecommerce.pagos.domain.OutboxEvent;
import com.aosorio.ecommerce.pagos.domain.Pago;
import com.aosorio.ecommerce.pagos.dto.PageResponseDTO;
import com.aosorio.ecommerce.pagos.dto.PagoRequestDTO;
import com.aosorio.ecommerce.pagos.dto.PagoResponseDTO;
import com.aosorio.ecommerce.pagos.exception.ResourceInUseException;
import com.aosorio.ecommerce.pagos.exception.ResourceNotFoundException;
import com.aosorio.ecommerce.pagos.mapper.PagoMapper;
import com.aosorio.ecommerce.pagos.repository.OutboxEventRepository;
import com.aosorio.ecommerce.pagos.repository.PagoRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PagoServiceImpl implements PagoService {

    private final PagoRepository pagoRepository;
    private final PagoMapper pagoMapper;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final PedidoClient pedidoClient;
    private final ConfiguracionService configuracionService;

    @Override
    @Transactional
    public PagoResponseDTO procesar(Long usuarioId, PagoRequestDTO request) {
        pedidoClient.validarPagable(request.getPedidoId(), usuarioId);
        return guardarPago(request.getPedidoId(), usuarioId, request.getMonto());
    }

    @Override
    @Transactional
    public PagoResponseDTO procesarDesdeEvento(OrderCreatedEvent event) {
        return pagoRepository.findFirstByPedidoIdOrderByIdDesc(event.pedidoId())
                .map(pagoMapper::toResponseDto)
                .orElseGet(() -> guardarPago(event.pedidoId(), event.usuarioId(), event.total()));
    }

    @Override
    @Transactional(readOnly = true)
    public PagoResponseDTO obtenerPorId(Long id) {
        Pago pago = pagoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el pago con id: " + id));
        return pagoMapper.toResponseDto(pago);
    }

    @Override
    @Transactional(readOnly = true)
    public PagoResponseDTO obtenerPorPedido(Long pedidoId) {
        Pago pago = pagoRepository.findFirstByPedidoIdOrderByIdDesc(pedidoId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró pago para el pedido con id: " + pedidoId));
        return pagoMapper.toResponseDto(pago);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PagoResponseDTO> obtenerTodos() {
        return pagoRepository.findAll().stream()
                .map(pagoMapper::toResponseDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PagoResponseDTO> obtenerPorUsuario(Long usuarioId) {
        return pagoRepository.findByUsuarioId(usuarioId).stream()
                .map(pagoMapper::toResponseDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<PagoResponseDTO> obtenerTodosPaginado(Pageable pageable) {
        return PageResponseDTO.from(
                pagoRepository.findAll(pageable).map(pagoMapper::toResponseDto)
        );
    }

    private PagoResponseDTO guardarPago(Long pedidoId, Long usuarioId, BigDecimal monto) {
        if (pagoRepository.existsByPedidoIdAndEstado(pedidoId, Pago.EstadoPago.PROCESADO)) {
            throw new ResourceInUseException("El pedido con id " + pedidoId + " ya está pagado");
        }

        int intento = pagoRepository.findFirstByPedidoIdOrderByIdDesc(pedidoId)
                .map(pago -> pago.getIntento() + 1)
                .orElse(1);

        BigDecimal montoMaximoAprobado = configuracionService.montoMaximoAprobado();
        Pago.EstadoPago estado = monto.compareTo(montoMaximoAprobado) > 0
                ? Pago.EstadoPago.RECHAZADO
                : Pago.EstadoPago.PROCESADO;

        String motivoRechazo = null;
        if (estado == Pago.EstadoPago.RECHAZADO) {
            motivoRechazo = "Monto excede el máximo aprobado (" + montoMaximoAprobado + ")";
        }

        Pago pago = Pago.builder()
                .pedidoId(pedidoId)
                .usuarioId(usuarioId)
                .monto(monto)
                .estado(estado)
                .motivoRechazo(motivoRechazo)
                .intento(intento)
                .build();

        Pago guardado = pagoRepository.save(pago);
        log.info("Se ha procesado el pago {} (intento {}) para pedido {} con estado {}",
                guardado.getId(), intento, pedidoId, estado);

        PaymentProcessedEvent event = new PaymentProcessedEvent(
                guardado.getId(),
                guardado.getPedidoId(),
                guardado.getUsuarioId(),
                guardado.getMonto(),
                guardado.getEstado().name(),
                guardado.getMotivoRechazo(),
                guardado.getIntento(),
                guardado.getFechaProcesado().toInstant(ZoneOffset.UTC)
        );

        try {
            outboxEventRepository.save(OutboxEvent.builder()
                    .agregadoId(guardado.getId())
                    .tipoEvento("PAYMENT_PROCESSED")
                    .payload(objectMapper.writeValueAsString(event))
                    .estado(OutboxEvent.EstadoOutbox.PENDIENTE)
                    .build());
            log.info("Evento PaymentProcessed del pago {} guardado en la tabla outbox", guardado.getId());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("No se pudo serializar el evento PaymentProcessed del pago: " + guardado.getId(), e);
        }

        return pagoMapper.toResponseDto(guardado);
    }
}
