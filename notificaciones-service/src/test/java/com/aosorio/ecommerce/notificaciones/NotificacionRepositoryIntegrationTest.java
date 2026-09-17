package com.aosorio.ecommerce.notificaciones;

import com.aosorio.ecommerce.notificaciones.domain.Notificacion;
import com.aosorio.ecommerce.notificaciones.repository.NotificacionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class NotificacionRepositoryIntegrationTest {

    @Autowired
    private NotificacionRepository notificacionRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Notificacion notificacion(Long usuarioId, String tipo, boolean leida) {
        return Notificacion.builder()
                .usuarioId(usuarioId)
                .tipo(Notificacion.TipoNotificacion.valueOf(tipo))
                .mensaje("Mensaje de prueba")
                .referenciaId(1L)
                .leida(leida)
                .build();
    }

    @Test
    void guardaNotificacionYAsignaIdYFechaDeCreacion() {
        Notificacion guardada = notificacionRepository.saveAndFlush(
                notificacion(1L, "PEDIDO_CREADO", false));

        assertThat(guardada.getId()).isNotNull();
        assertThat(guardada.getFechaCreacion()).isNotNull();
        assertThat(guardada.isLeida()).isFalse();
    }

    @Test
    void findByUsuarioIdRetornaSoloLasDelUsuario() {
        notificacionRepository.saveAll(List.of(
                notificacion(1L, "PEDIDO_CREADO", false),
                notificacion(1L, "PAGO_PROCESADO", true),
                notificacion(2L, "PEDIDO_CREADO", false)));
        entityManager.flush();
        entityManager.clear();

        List<Notificacion> delUsuario1 = notificacionRepository.findByUsuarioId(1L);

        assertThat(delUsuario1).hasSize(2);
        assertThat(delUsuario1).allSatisfy(n -> assertThat(n.getUsuarioId()).isEqualTo(1L));
    }

    @Test
    void findByUsuarioIdAndLeidaFiltraPorEstadoDeLectura() {
        notificacionRepository.saveAll(List.of(
                notificacion(1L, "PEDIDO_CREADO", false),
                notificacion(1L, "PAGO_PROCESADO", true),
                notificacion(1L, "PEDIDO_CREADO", false)));
        entityManager.flush();
        entityManager.clear();

        assertThat(notificacionRepository.findByUsuarioIdAndLeida(1L, false)).hasSize(2);
        assertThat(notificacionRepository.findByUsuarioIdAndLeida(1L, true)).hasSize(1);
    }

    @Test
    void marcarComoLeidaPersisteElCambio() {
        Notificacion guardada = notificacionRepository.saveAndFlush(
                notificacion(1L, "PAGO_PROCESADO", false));

        guardada.setLeida(true);
        notificacionRepository.saveAndFlush(guardada);
        entityManager.clear();

        Notificacion recargada = notificacionRepository.findById(guardada.getId()).orElseThrow();
        assertThat(recargada.isLeida()).isTrue();
    }
}