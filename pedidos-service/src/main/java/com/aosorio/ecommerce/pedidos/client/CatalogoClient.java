package com.aosorio.ecommerce.pedidos.client;

import com.aosorio.ecommerce.pedidos.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CatalogoClient {

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
}
