package com.aosorio.ecommerce.notificaciones.service;

import com.aosorio.ecommerce.events.OrderCreatedEvent;
import com.aosorio.ecommerce.events.PaymentProcessedEvent;
import com.aosorio.ecommerce.notificaciones.domain.Notificacion;
import com.aosorio.ecommerce.notificaciones.dto.NotificacionRequestDTO;
import com.aosorio.ecommerce.notificaciones.dto.NotificacionResponseDTO;
import com.aosorio.ecommerce.notificaciones.dto.PageResponseDTO;
import com.aosorio.ecommerce.notificaciones.exception.InvalidRequestException;
import com.aosorio.ecommerce.notificaciones.exception.ResourceNotFoundException;
import com.aosorio.ecommerce.notificaciones.mapper.NotificacionMapper;
import com.aosorio.ecommerce.notificaciones.repository.NotificacionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificacionServiceImpl implements NotificacionService {

    private final NotificacionRepository notificacionRepository;
    private final NotificacionMapper notificacionMapper;

    @Override
    @Transactional
    public NotificacionResponseDTO crear(NotificacionRequestDTO request) {
        Notificacion.TipoNotificacion tipo;
        try {
            tipo = Notificacion.TipoNotificacion.valueOf(request.getTipo().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("Tipo de notificación inválido: " + request.getTipo());
        }

        return guardar(request.getUsuarioId(), tipo, request.getMensaje(), request.getReferenciaId());
    }

    @Override
    @Transactional
    public NotificacionResponseDTO registrarPedidoCreado(OrderCreatedEvent event) {
        String mensaje = "Tu pedido #" + event.pedidoId() + " fue creado. Total: " + event.total();
        return guardar(
                event.usuarioId(),
                Notificacion.TipoNotificacion.PEDIDO_CREADO,
                mensaje,
                event.pedidoId()
        );
    }

    @Override
    @Transactional
    public NotificacionResponseDTO registrarPagoProcesado(PaymentProcessedEvent event) {
        String mensaje = "El pago #" + event.pagoId() + " del pedido #" + event.pedidoId()
                + " quedó en estado " + event.estado() + ". Monto: " + event.monto();
        return guardar(
                event.usuarioId(),
                Notificacion.TipoNotificacion.PAGO_PROCESADO,
                mensaje,
                event.pagoId()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public NotificacionResponseDTO obtenerPorId(Long id) {
        return notificacionMapper.toResponseDto(buscar(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificacionResponseDTO> obtenerTodos() {
        return notificacionRepository.findAll().stream()
                .map(notificacionMapper::toResponseDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificacionResponseDTO> obtenerPorUsuario(Long usuarioId) {
        return notificacionRepository.findByUsuarioId(usuarioId).stream()
                .map(notificacionMapper::toResponseDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificacionResponseDTO> obtenerNoLeidasPorUsuario(Long usuarioId) {
        return notificacionRepository.findByUsuarioIdAndLeida(usuarioId, false).stream()
                .map(notificacionMapper::toResponseDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<NotificacionResponseDTO> obtenerTodosPaginado(Pageable pageable) {
        return PageResponseDTO.from(
                notificacionRepository.findAll(pageable).map(notificacionMapper::toResponseDto)
        );
    }

    @Override
    @Transactional
    public NotificacionResponseDTO marcarComoLeida(Long id) {
        Notificacion notificacion = buscar(id);
        notificacion.setLeida(true);
        return notificacionMapper.toResponseDto(notificacionRepository.save(notificacion));
    }

    private NotificacionResponseDTO guardar(
            Long usuarioId,
            Notificacion.TipoNotificacion tipo,
            String mensaje,
            Long referenciaId
    ) {
        Notificacion notificacion = Notificacion.builder()
                .usuarioId(usuarioId)
                .tipo(tipo)
                .mensaje(mensaje)
                .referenciaId(referenciaId)
                .leida(false)
                .build();

        Notificacion guardada = notificacionRepository.save(notificacion);
        log.info("Notificación {} creada para usuario {} ({})", guardada.getId(), usuarioId, tipo);
        return notificacionMapper.toResponseDto(guardada);
    }

    private Notificacion buscar(Long id) {
        return notificacionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se encontró la notificación con id: " + id));
    }
}
