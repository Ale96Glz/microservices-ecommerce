package com.aosorio.ecommerce.pagos.client;

import com.aosorio.ecommerce.pagos.dto.PedidoEstadoDTO;
import com.aosorio.ecommerce.pagos.exception.AccessDeniedException;
import com.aosorio.ecommerce.pagos.exception.ResourceInUseException;
import com.aosorio.ecommerce.pagos.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PedidoClient {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final RestClient restClient;

    public PedidoClient(
            RestClient.Builder builder,
            @Value("${pedidos.service.url}") String pedidosBaseUrl,
            JwtPropagationInterceptor jwtPropagationInterceptor
    ) {
        this.restClient = builder
                .requestInterceptor(jwtPropagationInterceptor)
                .baseUrl(pedidosBaseUrl)
                .build();
    }

    public void validarPagable(Long pedidoId, Long usuarioId) {
        PedidoEstadoDTO pedido = restClient.get()
                .uri("/api/v1/pedido/{id}", pedidoId)
                .header(USER_ID_HEADER, String.valueOf(usuarioId))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    if (response.getStatusCode().value() == 404) {
                        throw new ResourceNotFoundException("No se encontró el pedido con id: " + pedidoId);
                    }
                    if (response.getStatusCode().value() == 403) {
                        throw new AccessDeniedException(
                                "El pedido con id " + pedidoId + " no pertenece al usuario autenticado");
                    }
                    throw new ResourceNotFoundException(
                            "No se pudo validar el pedido con id: " + pedidoId);
                })
                .body(PedidoEstadoDTO.class);

        if (pedido == null) {
            throw new ResourceNotFoundException("No se encontró el pedido con id: " + pedidoId);
        }
        if (!"CREADO".equals(pedido.estado())) {
            throw new ResourceInUseException(
                    "El pedido con id " + pedidoId + " no está en estado CREADO (estado actual: "
                            + pedido.estado() + ")");
        }
    }
}