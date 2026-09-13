package com.aosorio.ecommerce.pedidos.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

@Component
public class JwtPropagationInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        String authorization = currentAuthorization();
        if (authorization != null && !request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
            request.getHeaders().set(HttpHeaders.AUTHORIZATION, authorization);
        }
        return execution.execute(request, body);
    }

    private String currentAuthorization() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest current = attributes.getRequest();
        return current.getHeader(HttpHeaders.AUTHORIZATION);
    }
}