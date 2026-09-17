package com.aosorio.ecommerce.pedidos;

import com.aosorio.ecommerce.pedidos.domain.OutboxEvent;
import com.aosorio.ecommerce.pedidos.domain.Pedido;
import com.aosorio.ecommerce.pedidos.domain.PedidoItem;
import com.aosorio.ecommerce.pedidos.repository.OutboxEventRepository;
import com.aosorio.ecommerce.pedidos.repository.PedidoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PedidoRepositoryIntegrationTest {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Pedido pedido(Long usuarioId, String estado, String... productos) {
        Pedido pedido = Pedido.builder()
                .usuarioId(usuarioId)
                .total(new BigDecimal("100.00"))
                .estado(Pedido.EstadoPedido.valueOf(estado))
                .build();

        int i = 0;
        for (String nombre : productos) {
            pedido.agregarItem(PedidoItem.builder()
                    .productoId((long) (++i))
                    .nombreProducto(nombre)
                    .precioUnitario(new BigDecimal("50.00"))
                    .cantidad(2)
                    .subtotal(new BigDecimal("100.00"))
                    .build());
        }
        return pedido;
    }

    @Test
    void guardaPedidoConItemsEnCascada() {
        Pedido guardado = pedidoRepository.saveAndFlush(pedido(1L, "CREADO", "Laptop", "Mouse"));

        assertThat(guardado.getId()).isNotNull();
        assertThat(guardado.getFechaCreacion()).isNotNull();
        assertThat(guardado.getItems()).hasSize(2);
        assertThat(guardado.getItems()).allSatisfy(item ->
                assertThat(item.getId()).isNotNull());
    }

    @Test
    void findWithItemsByIdTraeLosItemsAsociados() {
        Pedido guardado = pedidoRepository.saveAndFlush(pedido(1L, "CREADO", "Laptop"));
        entityManager.clear();

        Optional<Pedido> encontrado = pedidoRepository.findWithItemsById(guardado.getId());

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getItems()).hasSize(1);
        assertThat(encontrado.get().getItems().get(0).getNombreProducto()).isEqualTo("Laptop");
    }

    @Test
    void findByUsuarioIdFiltraPorUsuario() {
        pedidoRepository.saveAll(List.of(
                pedido(1L, "CREADO", "Laptop"),
                pedido(1L, "PAGADO", "Mouse"),
                pedido(2L, "CREADO", "Teclado")));
        entityManager.flush();
        entityManager.clear();

        List<Pedido> deUsuario1 = pedidoRepository.findByUsuarioId(1L);

        assertThat(deUsuario1).hasSize(2);
        assertThat(deUsuario1).allSatisfy(p -> assertThat(p.getUsuarioId()).isEqualTo(1L));
    }

    @Test
    void findAllPaginadoTraeLosItems() {
        pedidoRepository.saveAll(List.of(
                pedido(1L, "CREADO", "A"),
                pedido(1L, "CREADO", "B"),
                pedido(2L, "CREADO", "C")));
        entityManager.flush();
        entityManager.clear();

        Page<Pedido> pagina = pedidoRepository.findAll(PageRequest.of(0, 2, Sort.by("id").ascending()));

        assertThat(pagina.getTotalElements()).isEqualTo(3);
        assertThat(pagina.getContent()).hasSize(2);
        assertThat(pagina.getContent().get(0).getItems()).isNotEmpty();
    }

    @Test
    void eliminarPedidoEliminaSusItemsPorOrphanRemoval() {
        Pedido guardado = pedidoRepository.saveAndFlush(pedido(1L, "CREADO", "Laptop", "Mouse"));
        Long pedidoId = guardado.getId();
        entityManager.clear();

        pedidoRepository.deleteById(pedidoId);
        entityManager.flush();
        entityManager.clear();

        assertThat(pedidoRepository.findById(pedidoId)).isEmpty();
        Long itemsRestantes = entityManager.getEntityManager()
                .createQuery("SELECT COUNT(i) FROM PedidoItem i", Long.class)
                .getSingleResult();
        assertThat(itemsRestantes).isZero();
    }

    @Test
    void outboxRepositoryFiltraPorEstado() {
        outboxEventRepository.saveAll(List.of(
                OutboxEvent.builder()
                        .agregadoId(1L)
                        .tipoEvento("OrderCreatedEvent")
                        .payload("{}")
                        .estado(OutboxEvent.EstadoOutbox.PENDIENTE)
                        .build(),
                OutboxEvent.builder()
                        .agregadoId(2L)
                        .tipoEvento("OrderCreatedEvent")
                        .payload("{}")
                        .estado(OutboxEvent.EstadoOutbox.PUBLICADO)
                        .build()));

        assertThat(outboxEventRepository.findByEstado(OutboxEvent.EstadoOutbox.PENDIENTE)).hasSize(1);
        assertThat(outboxEventRepository.findByEstado(OutboxEvent.EstadoOutbox.PUBLICADO)).hasSize(1);
    }
}