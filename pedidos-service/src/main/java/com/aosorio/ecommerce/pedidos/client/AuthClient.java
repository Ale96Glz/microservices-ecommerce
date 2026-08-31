package com.aosorio.ecommerce.pedidos.client;

import com.aosorio.ecommerce.pedidos.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AuthClient {

    private static final String USER_ID_HEADER = "X-User-Id";

    private final RestClient restClient;

    public AuthClient(
            RestClient.Builder builder,
            @Value("${auth.service.url}") String authBaseUrl
    ) {
        this.restClient = builder.baseUrl(authBaseUrl).build();
    }

    public UsuarioValidacionDTO validarUsuario(Long usuarioId) {
        return restClient.get()
                .uri("/api/v1/usuario/existe/{id}", usuarioId)
                .header(USER_ID_HEADER, String.valueOf(usuarioId))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    throw new ResourceNotFoundException("No existe un usuario válido con id: " + usuarioId);
                })
                .body(UsuarioValidacionDTO.class);
    }
}
