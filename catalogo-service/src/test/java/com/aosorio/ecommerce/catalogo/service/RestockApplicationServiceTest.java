package com.aosorio.ecommerce.catalogo.service;

import com.aosorio.ecommerce.catalogo.domain.RestockEvent;
import com.aosorio.ecommerce.catalogo.repository.RestockEventRepository;
import com.aosorio.ecommerce.events.RestockRequestedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestockApplicationServiceTest {

    @Mock
    private RestockEventRepository restockEventRepository;
    @Mock
    private ProductoService productoService;

    @InjectMocks
    private RestockApplicationService restockApplicationService;

    private RestockRequestedEvent evento(String eventId) {
        return new RestockRequestedEvent(eventId, 5L, 3L, 2, Instant.parse("2026-09-18T10:05:00Z"));
    }

    @Test
    void aplicaRestockYRegistraElEventoCuandoEsNuevo() {
        when(restockEventRepository.existsById("5-3")).thenReturn(false);

        restockApplicationService.aplicar(evento("5-3"));

        verify(productoService).reponerStock(3L, 2);
        verify(restockEventRepository).save(any(RestockEvent.class));
    }

    @Test
    void ignoraElEventoCuandoYaFueAplicado() {
        when(restockEventRepository.existsById("5-3")).thenReturn(true);

        restockApplicationService.aplicar(evento("5-3"));

        verify(productoService, never()).reponerStock(any(Long.class), any(Integer.class));
        verify(restockEventRepository, never()).save(any(RestockEvent.class));
    }

    @Test
    void enUnEventoNuevoSeGuardaElRestockConLosDatosDelPedido() {
        when(restockEventRepository.existsById("5-3")).thenReturn(false);

        restockApplicationService.aplicar(evento("5-3"));

        var captor = org.mockito.ArgumentCaptor.forClass(RestockEvent.class);
        verify(restockEventRepository).save(captor.capture());
        RestockEvent guardado = captor.getValue();
        assertThat(guardado.getEventId()).isEqualTo("5-3");
        assertThat(guardado.getPedidoId()).isEqualTo(5L);
        assertThat(guardado.getProductoId()).isEqualTo(3L);
        assertThat(guardado.getCantidad()).isEqualTo(2);
    }
}