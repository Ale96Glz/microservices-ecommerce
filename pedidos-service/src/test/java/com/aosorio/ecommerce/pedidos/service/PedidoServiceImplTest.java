package com.aosorio.ecommerce.pedidos.service;

import com.aosorio.ecommerce.events.PaymentProcessedEvent;
import com.aosorio.ecommerce.pedidos.client.AuthClient;
import com.aosorio.ecommerce.pedidos.client.CatalogoClient;
import com.aosorio.ecommerce.pedidos.client.ProductoCatalogoDTO;
import com.aosorio.ecommerce.pedidos.domain.OutboxEvent;
import com.aosorio.ecommerce.pedidos.domain.Pedido;
import com.aosorio.ecommerce.pedidos.domain.PedidoItem;
import com.aosorio.ecommerce.pedidos.dto.PedidoItemRequestDTO;
import com.aosorio.ecommerce.pedidos.dto.PedidoRequestDTO;
import com.aosorio.ecommerce.pedidos.dto.PedidoResponseDTO;
import com.aosorio.ecommerce.pedidos.exception.ResourceInUseException;
import com.aosorio.ecommerce.pedidos.exception.ResourceNotFoundException;
import com.aosorio.ecommerce.pedidos.mapper.PedidoMapper;
import com.aosorio.ecommerce.pedidos.repository.OutboxEventRepository;
import com.aosorio.ecommerce.pedidos.repository.PedidoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoServiceImplTest {

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private CatalogoClient catalogoClient;
    @Mock
    private AuthClient authClient;
    @Mock
    private OutboxEventRepository outboxEventRepository;

    private PedidoMapper pedidoMapper;
    private ObjectMapper objectMapper;
    private PedidoServiceImpl pedidoService;

    @BeforeEach
    void setUp() {
        pedidoMapper = new PedidoMapper();
        objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        pedidoService = new PedidoServiceImpl(
                pedidoRepository, catalogoClient, authClient, pedidoMapper, outboxEventRepository, objectMapper);
    }

    private ProductoCatalogoDTO producto(Long id, String nombre, int stock, String estado) {
        return new ProductoCatalogoDTO(id, nombre, "Desc", new BigDecimal("100.00"), stock, estado, "Cat",
                LocalDateTime.now());
    }

    private PedidoItemRequestDTO item(Long productoId, int cantidad) {
        return PedidoItemRequestDTO.builder().productoId(productoId).cantidad(cantidad).build();
    }

    private PedidoRequestDTO request(Long productoId, int cantidad) {
        return PedidoRequestDTO.builder().items(List.of(item(productoId, cantidad))).build();
    }

    private Pedido pedido(Long id, String estado, Long usuarioId) {
        Pedido pedido = Pedido.builder()
                .id(id)
                .usuarioId(usuarioId)
                .total(new BigDecimal("100.00"))
                .estado(Pedido.EstadoPedido.valueOf(estado))
                .fechaCreacion(LocalDateTime.now())
                .build();
        PedidoItem item = PedidoItem.builder()
                .productoId(1L)
                .nombreProducto("Laptop Pro")
                .precioUnitario(new BigDecimal("100.00"))
                .cantidad(1)
                .subtotal(new BigDecimal("100.00"))
                .build();
        pedido.agregarItem(item);
        return pedido;
    }

    @Test
    void crearCalculaTotalYGuardaEventoEnOutbox() {
        when(authClient.validarUsuario(9L)).thenReturn(null);
        when(catalogoClient.obtenerProducto(1L)).thenReturn(producto(1L, "Laptop Pro", 10, "ACTIVO"));
        when(pedidoRepository.save(any(Pedido.class))).thenReturn(pedido(5L, "CREADO", 9L));

        PedidoResponseDTO respuesta = pedidoService.crear(9L, request(1L, 2));

        verify(authClient).validarUsuario(9L);
        verify(catalogoClient).descontarStock(9L, 1L, 2);
        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getTipoEvento()).isEqualTo("ORDER_CREATED");
        assertThat(outboxCaptor.getValue().getEstado()).isEqualTo(OutboxEvent.EstadoOutbox.PENDIENTE);
        assertThat(outboxCaptor.getValue().getPayload()).contains("pedidoId");
        assertThat(respuesta.id()).isEqualTo(5L);
    }

    @Test
    void crearConProductoAgotadoLanzaResourceInUse() {
        when(authClient.validarUsuario(9L)).thenReturn(null);
        when(catalogoClient.obtenerProducto(1L)).thenReturn(producto(1L, "Laptop Pro", 0, "AGOTADO"));

        assertThatThrownBy(() -> pedidoService.crear(9L, request(1L, 2)))
                .isInstanceOf(ResourceInUseException.class)
                .hasMessageContaining("Stock insuficiente");

        verify(pedidoRepository, never()).save(any());
    }

    @Test
    void crearConStockInsuficienteLanzaResourceInUse() {
        when(authClient.validarUsuario(9L)).thenReturn(null);
        when(catalogoClient.obtenerProducto(1L)).thenReturn(producto(1L, "Laptop Pro", 1, "ACTIVO"));

        assertThatThrownBy(() -> pedidoService.crear(9L, request(1L, 5)))
                .isInstanceOf(ResourceInUseException.class)
                .hasMessageContaining("Disponible: 1");
    }

    @Test
    void cancelarPedidoEncolaRestockYCambiaEstado() {
        when(pedidoRepository.findWithItemsById(5L)).thenReturn(Optional.of(pedido(5L, "CREADO", 9L)));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PedidoResponseDTO respuesta = pedidoService.cancelar(5L);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getTipoEvento()).isEqualTo(OutboxEvent.TIPO_RESTOCK_REQUIRED);
        assertThat(outboxCaptor.getValue().getAgregadoId()).isEqualTo(5L);
        assertThat(outboxCaptor.getValue().getPayload()).contains("productoId");
        assertThat(outboxCaptor.getValue().getPayload()).contains("\"eventId\":\"5-1-usuario\"");
        assertThat(respuesta.estado()).isEqualTo("CANCELADO");
        assertThat(respuesta.motivoCancelacion()).isEqualTo("USUARIO");
    }

    @Test
    void cancelarPedidoYaLiberadoNoSePermite() {
        when(pedidoRepository.findWithItemsById(5L)).thenReturn(Optional.of(pedido(5L, "PAGADO", 9L)));

        assertThatThrownBy(() -> pedidoService.cancelar(5L))
                .isInstanceOf(ResourceInUseException.class);

        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    void procesarResultadoPagoExitosoMarcaPpagado() {
        when(pedidoRepository.findWithItemsById(5L)).thenReturn(Optional.of(pedido(5L, "CREADO", 9L)));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PedidoResponseDTO respuesta = pedidoService.procesarResultadoPago(
                new PaymentProcessedEvent(1L, 5L, 9L, new BigDecimal("100.00"), "PROCESADO", null, 1, Instant.now()));

        assertThat(respuesta.estado()).isEqualTo("PAGADO");
        assertThat(respuesta.motivoCancelacion()).isNull();
    }

    @Test
    void procesarResultadoPagoRechazadoCancelaYEncolaRestock() {
        when(pedidoRepository.findWithItemsById(5L)).thenReturn(Optional.of(pedido(5L, "CREADO", 9L)));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PedidoResponseDTO respuesta = pedidoService.procesarResultadoPago(
                new PaymentProcessedEvent(1L, 5L, 9L, new BigDecimal("100.00"), "RECHAZADO", null, 1, Instant.now()));

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getTipoEvento()).isEqualTo(OutboxEvent.TIPO_RESTOCK_REQUIRED);
        assertThat(outboxCaptor.getValue().getPayload()).contains("\"eventId\":\"5-1-intento1\"");
        assertThat(respuesta.estado()).isEqualTo("CANCELADO");
        assertThat(respuesta.motivoCancelacion()).isEqualTo("PAGO_RECHAZADO");
    }

    @Test
    void procesarResultadoPagoConPedidoNoExistenteLanzaNotFound() {
        when(pedidoRepository.findWithItemsById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pedidoService.procesarResultadoPago(
                new PaymentProcessedEvent(1L, 99L, 9L, new BigDecimal("100.00"), "PROCESADO", null, 1, Instant.now())))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reactivarRevalidaYReReservaStockYDevuelveAcreado() {
        Pedido cancelado = pedido(5L, "CANCELADO", 9L);
        cancelado.setMotivoCancelacion(Pedido.MotivoCancelacion.PAGO_RECHAZADO);
        when(pedidoRepository.findWithItemsById(5L)).thenReturn(Optional.of(cancelado));
        when(catalogoClient.obtenerProducto(1L)).thenReturn(producto(1L, "Laptop Pro", 10, "ACTIVO"));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PedidoResponseDTO respuesta = pedidoService.reactivar(5L);

        verify(catalogoClient).descontarStock(9L, 1L, 1);
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
        assertThat(respuesta.estado()).isEqualTo("CREADO");
        assertThat(respuesta.motivoCancelacion()).isNull();
    }

    @Test
    void reactivarSoloPermiteCanceladoPorRechazoDePago() {
        Pedido canceladoPorUsuario = pedido(5L, "CANCELADO", 9L);
        canceladoPorUsuario.setMotivoCancelacion(Pedido.MotivoCancelacion.USUARIO);
        when(pedidoRepository.findWithItemsById(5L)).thenReturn(Optional.of(canceladoPorUsuario));

        assertThatThrownBy(() -> pedidoService.reactivar(5L))
                .isInstanceOf(ResourceInUseException.class);

        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
        verify(catalogoClient, never()).descontarStock(any(), any(), anyInt());
    }

    @Test
    void reactivarConStockInsuficienteLanzaResourceInUseYNoCambiaElEstado() {
        Pedido cancelado = pedido(5L, "CANCELADO", 9L);
        cancelado.setMotivoCancelacion(Pedido.MotivoCancelacion.PAGO_RECHAZADO);
        when(pedidoRepository.findWithItemsById(5L)).thenReturn(Optional.of(cancelado));
        when(catalogoClient.obtenerProducto(1L)).thenReturn(producto(1L, "Laptop Pro", 0, "AGOTADO"));

        assertThatThrownBy(() -> pedidoService.reactivar(5L))
                .isInstanceOf(ResourceInUseException.class)
                .hasMessageContaining("Stock insuficiente");

        verify(pedidoRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    void obtenerPorIdInexistenteLanzaNotFound() {
        when(pedidoRepository.findWithItemsById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pedidoService.obtenerPorId(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}