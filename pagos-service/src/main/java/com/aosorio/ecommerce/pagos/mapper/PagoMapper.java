package com.aosorio.ecommerce.pagos.mapper;

import com.aosorio.ecommerce.pagos.domain.Pago;
import com.aosorio.ecommerce.pagos.dto.PagoResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class PagoMapper {

    public PagoResponseDTO toResponseDto(Pago pago) {
        return new PagoResponseDTO(
                pago.getId(),
                pago.getPedidoId(),
                pago.getUsuarioId(),
                pago.getMonto(),
                pago.getEstado() != null ? pago.getEstado().name() : null,
                pago.getFechaProcesado()
        );
    }
}
