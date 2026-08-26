package com.aosorio.ecommerce.pagos.service;

import com.aosorio.ecommerce.events.OrderCreatedEvent;
import com.aosorio.ecommerce.events.PaymentProcessedEvent;
import com.aosorio.ecommerce.pagos.domain.Pago;
import com.aosorio.ecommerce.pagos.dto.PageResponseDTO;
import com.aosorio.ecommerce.pagos.dto.PagoRequestDTO;
import com.aosorio.ecommerce.pagos.dto.PagoResponseDTO;
import com.aosorio.ecommerce.pagos.event.PaymentEventPublisher;
import com.aosorio.ecommerce.pagos.exception.ResourceInUseException;
import com.aosorio.ecommerce.pagos.exception.ResourceNotFoundException;
import com.aosorio.ecommerce.pagos.mapper.PagoMapper;
import com.aosorio.ecommerce.pagos.repository.PagoRepository;
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
    private final PaymentEventPublisher paymentEventPublisher;

    @Override
    @Transactional
    public PagoResponseDTO procesar(PagoRequestDTO request) {
        return guardarPago(request.getPedidoId(), request.getUsuarioId(), request.getMonto());
    }

    @Override
    @Transactional
    public PagoResponseDTO procesarDesdeEvento(OrderCreatedEvent event) {
        return pagoRepository.findByPedidoId(event.pedidoId())
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
        Pago pago = pagoRepository.findByPedidoId(pedidoId)
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
        if (pagoRepository.existsByPedidoId(pedidoId)) {
            throw new ResourceInUseException("Ya existe un pago para el pedido con id: " + pedidoId);
        }

        Pago pago = Pago.builder()
                .pedidoId(pedidoId)
                .usuarioId(usuarioId)
                .monto(monto)
                .estado(Pago.EstadoPago.PROCESADO)
                .build();

        Pago guardado = pagoRepository.save(pago);
        log.info("Se ha procesado el pago {} para pedido {}", guardado.getId(), pedidoId);

        paymentEventPublisher.publish(new PaymentProcessedEvent(
                guardado.getId(),
                guardado.getPedidoId(),
                guardado.getUsuarioId(),
                guardado.getMonto(),
                guardado.getEstado().name(),
                guardado.getFechaProcesado().toInstant(ZoneOffset.UTC)
        ));

        return pagoMapper.toResponseDto(guardado);
    }
}
