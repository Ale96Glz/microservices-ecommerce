package com.aosorio.ecommerce.pedidos.client;

import com.aosorio.ecommerce.pedidos.exception.InvalidStockException;
import com.aosorio.ecommerce.pedidos.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CatalogoClient {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final RestClient restClient;

    public CatalogoClient(
            RestClient.Builder builder,
            @Value("${catalogo.service.url}") String catalogoBaseUrl
    ) {
        this.restClient = builder.baseUrl(catalogoBaseUrl).build();
    }

    public ProductoCatalogoDTO obtenerProducto(Long productoId) {
        return restClient.get()
                .uri("/api/v1/producto/{id}", productoId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    throw new ResourceNotFoundException("No existe el producto con id: " + productoId);
                })
                .body(ProductoCatalogoDTO.class);
    }

    public void descontarStock(Long usuarioId, Long productoId, int cantidad) {
        restClient.put()
                .uri("/api/v1/producto/{id}/stock?cantidad={cantidad}", productoId, cantidad)
                .header(USER_ID_HEADER, String.valueOf(usuarioId))
                .contentType(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    throw new InvalidStockException(
                            "No se pudo reservar stock para el producto " + productoId
                                    + ". Cantidad solicitada: " + cantidad);
                })
                .toBodilessEntity();
    }
}
