package com.aosorio.ecommerce.pedidos.service;

import com.aosorio.ecommerce.events.OrderCreatedEvent;
import com.aosorio.ecommerce.pedidos.client.CatalogoClient;
import com.aosorio.ecommerce.pedidos.client.ProductoCatalogoDTO;
import com.aosorio.ecommerce.pedidos.domain.Pedido;
import com.aosorio.ecommerce.pedidos.domain.PedidoItem;
import com.aosorio.ecommerce.pedidos.dto.PageResponseDTO;
import com.aosorio.ecommerce.pedidos.dto.PedidoItemRequestDTO;
import com.aosorio.ecommerce.pedidos.dto.PedidoRequestDTO;
import com.aosorio.ecommerce.pedidos.dto.PedidoResponseDTO;
import com.aosorio.ecommerce.pedidos.event.OrderEventPublisher;
import com.aosorio.ecommerce.pedidos.exception.ResourceInUseException;
import com.aosorio.ecommerce.pedidos.exception.ResourceNotFoundException;
import com.aosorio.ecommerce.pedidos.mapper.PedidoMapper;
import com.aosorio.ecommerce.pedidos.repository.PedidoRepository;
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
public class PedidoServiceImpl implements PedidoService {

    private final PedidoRepository pedidoRepository;
    private final CatalogoClient catalogoClient;
    private final PedidoMapper pedidoMapper;
    private final OrderEventPublisher orderEventPublisher;

    @Override
    @Transactional
    public PedidoResponseDTO crear(Long usuarioId, PedidoRequestDTO pedidoRequestDTO) {
        log.info("Iniciando la creacion del pedido para usuario: {}", usuarioId);

        Pedido pedido = Pedido.builder()
                .usuarioId(usuarioId)
                .estado(Pedido.EstadoPedido.CREADO)
                .total(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (PedidoItemRequestDTO itemRequest : pedidoRequestDTO.getItems()) {
            ProductoCatalogoDTO producto = catalogoClient.obtenerProducto(itemRequest.getProductoId());
            validarProducto(producto, itemRequest.getCantidad());

            BigDecimal subtotal = producto.precio().multiply(BigDecimal.valueOf(itemRequest.getCantidad()));
            PedidoItem item = PedidoItem.builder()
                    .productoId(producto.id())
                    .nombreProducto(producto.nombre())
                    .precioUnitario(producto.precio())
                    .cantidad(itemRequest.getCantidad())
                    .subtotal(subtotal)
                    .build();
            pedido.agregarItem(item);
            total = total.add(subtotal);
        }

        pedido.setTotal(total);
        Pedido guardado = pedidoRepository.save(pedido);
        log.info("Se ha guardado el pedido: {}", guardado.getId());

        orderEventPublisher.publish(new OrderCreatedEvent(
                guardado.getId(),
                guardado.getUsuarioId(),
                guardado.getTotal(),
                guardado.getFechaCreacion().toInstant(ZoneOffset.UTC)
        ));

        return pedidoMapper.toResponseDto(guardado);
    }

    @Override
    @Transactional
    public PedidoResponseDTO cancelar(Long id) {
        Pedido pedido = pedidoRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el pedido con id: " + id));

        if (pedido.getEstado() != Pedido.EstadoPedido.CREADO) {
            throw new ResourceInUseException(
                    "No se puede cancelar el pedido " + id + " porque está en estado " + pedido.getEstado());
        }

        pedido.setEstado(Pedido.EstadoPedido.CANCELADO);
        Pedido cancelado = pedidoRepository.save(pedido);
        log.info("Se ha cancelado el pedido: {}", cancelado.getId());
        return pedidoMapper.toResponseDto(cancelado);
    }

    @Override
    @Transactional(readOnly = true)
    public PedidoResponseDTO obtenerPorId(Long id) {
        Pedido pedido = pedidoRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el pedido con id: " + id));
        log.info("Se ha obtenido el pedido: {}", pedido.getId());
        return pedidoMapper.toResponseDto(pedido);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> obtenerTodos() {
        List<Pedido> pedidos = pedidoRepository.findAll();
        log.info("Se han obtenido {} pedidos", pedidos.size());
        return pedidos.stream().map(pedidoMapper::toResponseDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PedidoResponseDTO> obtenerPorUsuario(Long usuarioId) {
        return pedidoRepository.findByUsuarioId(usuarioId).stream()
                .map(pedidoMapper::toResponseDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<PedidoResponseDTO> obtenerTodosPaginado(Pageable pageable) {
        return PageResponseDTO.from(
                pedidoRepository.findAll(pageable).map(pedidoMapper::toResponseDto)
        );
    }

    private void validarProducto(ProductoCatalogoDTO producto, int cantidad) {
        if ("AGOTADO".equalsIgnoreCase(producto.estado()) || producto.stock() < cantidad) {
            throw new ResourceInUseException(
                    "Stock insuficiente para el producto " + producto.id()
                            + ". Disponible: " + producto.stock()
                            + ", solicitado: " + cantidad);
        }
    }
}
