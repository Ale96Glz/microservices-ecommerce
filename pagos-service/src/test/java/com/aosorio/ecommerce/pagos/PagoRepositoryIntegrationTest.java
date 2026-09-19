package com.aosorio.ecommerce.pagos;

import com.aosorio.ecommerce.pagos.domain.OutboxEvent;
import com.aosorio.ecommerce.pagos.domain.Pago;
import com.aosorio.ecommerce.pagos.repository.OutboxEventRepository;
import com.aosorio.ecommerce.pagos.repository.PagoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class PagoRepositoryIntegrationTest {

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Pago pago(Long pedidoId, Long usuarioId, String estado, Integer intento) {
        return Pago.builder()
                .pedidoId(pedidoId)
                .usuarioId(usuarioId)
                .monto(new BigDecimal("250.00"))
                .estado(Pago.EstadoPago.valueOf(estado))
                .intento(intento)
                .build();
    }

    @Test
    void guardaPagoYAsignaIdYFecha() {
        Pago guardado = pagoRepository.saveAndFlush(pago(10L, 1L, "PROCESADO", 1));

        assertThat(guardado.getId()).isNotNull();
        assertThat(guardado.getFechaProcesado()).isNotNull();
    }

    @Test
    void findFirstByPedidoIdOrderByIdDescRetornaElUltimoIntento() {
        pagoRepository.saveAndFlush(pago(20L, 1L, "RECHAZADO", 1));
        pagoRepository.saveAndFlush(pago(20L, 1L, "PROCESADO", 2));
        entityManager.clear();

        Optional<Pago> encontrado = pagoRepository.findFirstByPedidoIdOrderByIdDesc(20L);

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getIntento()).isEqualTo(2);
        assertThat(encontrado.get().getEstado()).isEqualTo(Pago.EstadoPago.PROCESADO);
    }

    @Test
    void findFirstByPedidoIdInexistenteRetornaVacio() {
        assertThat(pagoRepository.findFirstByPedidoIdOrderByIdDesc(999L)).isEmpty();
    }

    @Test
    void existsProcesadoReflejaElEstado() {
        pagoRepository.saveAndFlush(pago(30L, 1L, "RECHAZADO", 1));

        assertThat(pagoRepository.existsByPedidoIdAndEstado(30L, Pago.EstadoPago.PROCESADO)).isFalse();
        assertThat(pagoRepository.existsByPedidoIdAndEstado(30L, Pago.EstadoPago.RECHAZADO)).isTrue();
        assertThat(pagoRepository.existsByPedidoIdAndEstado(31L, Pago.EstadoPago.PROCESADO)).isFalse();
    }

    @Test
    void findByUsuarioIdFiltraPorUsuario() {
        pagoRepository.saveAll(List.of(
                pago(40L, 1L, "PROCESADO", 1),
                pago(41L, 1L, "PROCESADO", 1),
                pago(42L, 2L, "PROCESADO", 1)));

        List<Pago> deUsuario1 = pagoRepository.findByUsuarioId(1L);

        assertThat(deUsuario1).hasSize(2);
        assertThat(deUsuario1).allSatisfy(p -> assertThat(p.getUsuarioId()).isEqualTo(1L));
    }

    @Test
    void noPermiteDosPagosConElMismoPedidoEIntento() {
        pagoRepository.saveAndFlush(pago(50L, 1L, "PROCESADO", 1));

        assertThatThrownBy(() -> pagoRepository.saveAndFlush(pago(50L, 1L, "PROCESADO", 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void permiteNuevoIntentoTrasUnRechazo() {
        pagoRepository.saveAndFlush(pago(60L, 1L, "RECHAZADO", 1));

        pagoRepository.saveAndFlush(pago(60L, 1L, "PROCESADO", 2));

        assertThat(pagoRepository.findFirstByPedidoIdOrderByIdDesc(60L)).isPresent();
    }

    @Test
    void outboxRepositoryFiltraPorEstado() {
        outboxEventRepository.saveAll(List.of(
                OutboxEvent.builder()
                        .agregadoId(1L)
                        .tipoEvento("PaymentProcessedEvent")
                        .payload("{}")
                        .estado(OutboxEvent.EstadoOutbox.PENDIENTE)
                        .build(),
                OutboxEvent.builder()
                        .agregadoId(2L)
                        .tipoEvento("PaymentProcessedEvent")
                        .payload("{}")
                        .estado(OutboxEvent.EstadoOutbox.PUBLICADO)
                        .build()));

        assertThat(outboxEventRepository.findByEstado(OutboxEvent.EstadoOutbox.PENDIENTE)).hasSize(1);
        assertThat(outboxEventRepository.findByEstado(OutboxEvent.EstadoOutbox.PUBLICADO)).hasSize(1);
    }
}