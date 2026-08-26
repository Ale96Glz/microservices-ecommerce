package com.aosorio.ecommerce.pedidos.mapper;

import com.aosorio.ecommerce.pedidos.domain.Pedido;
import com.aosorio.ecommerce.pedidos.domain.PedidoItem;
import com.aosorio.ecommerce.pedidos.dto.PedidoItemResponseDTO;
import com.aosorio.ecommerce.pedidos.dto.PedidoResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class PedidoMapper {

    public PedidoResponseDTO toResponseDto(Pedido pedido) {
        return new PedidoResponseDTO(
                pedido.getId(),
                pedido.getUsuarioId(),
                pedido.getTotal(),
                pedido.getEstado() != null ? pedido.getEstado().name() : null,
                pedido.getItems().stream().map(this::toItemResponseDto).toList(),
                pedido.getFechaCreacion()
        );
    }

    private PedidoItemResponseDTO toItemResponseDto(PedidoItem item) {
        return new PedidoItemResponseDTO(
                item.getProductoId(),
                item.getNombreProducto(),
                item.getPrecioUnitario(),
                item.getCantidad(),
                item.getSubtotal()
        );
    }
}
