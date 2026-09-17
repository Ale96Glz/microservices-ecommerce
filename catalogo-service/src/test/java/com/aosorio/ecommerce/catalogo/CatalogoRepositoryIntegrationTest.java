package com.aosorio.ecommerce.catalogo;

import com.aosorio.ecommerce.catalogo.domain.Categoria;
import com.aosorio.ecommerce.catalogo.domain.Producto;
import com.aosorio.ecommerce.catalogo.repository.CategoriaRepository;
import com.aosorio.ecommerce.catalogo.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CatalogoRepositoryIntegrationTest {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Categoria categoria;

    @BeforeEach
    void setUp() {
        categoria = entityManager.persist(Categoria.builder()
                .nombre("Computo")
                .descripcion("Equipos de computo")
                .build());
        entityManager.flush();
    }

    private Producto producto(String nombre, int stock) {
        return Producto.builder()
                .nombre(nombre)
                .descripcion("descripcion")
                .precio(new BigDecimal("99.90"))
                .stock(stock)
                .categoria(categoria)
                .estado(stock > 0 ? Producto.EstadoProducto.ACTIVO : Producto.EstadoProducto.AGOTADO)
                .build();
    }

    @Test
    void descontarStockReduceCuandoHayDisponible() {
        Producto guardado = productoRepository.saveAndFlush(producto("Laptop", 10));
        entityManager.clear();

        int filas = productoRepository.descontarStock(guardado.getId(), 4);
        entityManager.flush();
        entityManager.clear();

        assertThat(filas).isEqualTo(1);
        assertThat(productoRepository.findById(guardado.getId()).orElseThrow().getStock()).isEqualTo(6);
    }

    @Test
    void descontarStockNoAfectaSiNoHaySuficiente() {
        Producto guardado = productoRepository.saveAndFlush(producto("Laptop", 2));
        entityManager.clear();

        int filas = productoRepository.descontarStock(guardado.getId(), 5);
        entityManager.flush();
        entityManager.clear();

        assertThat(filas).isZero();
        assertThat(productoRepository.findById(guardado.getId()).orElseThrow().getStock()).isEqualTo(2);
    }

    @Test
    void reponerStockIncrementaElValor() {
        Producto guardado = productoRepository.saveAndFlush(producto("Laptop", 1));
        entityManager.clear();

        int filas = productoRepository.reponerStock(guardado.getId(), 9);
        entityManager.flush();
        entityManager.clear();

        assertThat(filas).isEqualTo(1);
        assertThat(productoRepository.findById(guardado.getId()).orElseThrow().getStock()).isEqualTo(10);
    }

    @Test
    void countByCategoriaIdCuentaLosProductosDeLaCategoria() {
        productoRepository.saveAll(java.util.List.of(producto("A", 1), producto("B", 2)));

        assertThat(productoRepository.countByCategoriaId(categoria.getId())).isEqualTo(2);
    }

    @Test
    void existsByNombreYExistsByCategoriaId() {
        productoRepository.saveAndFlush(producto("Monitor", 3));

        assertThat(productoRepository.existsByNombre("Monitor")).isTrue();
        assertThat(productoRepository.existsByNombre("Inexistente")).isFalse();
        assertThat(productoRepository.existsByCategoriaId(categoria.getId())).isTrue();
    }

    @Test
    void findByNombreContainingIgnoreCaseEsCaseInsensitive() {
        productoRepository.saveAndFlush(producto("Teclado Mecanico", 5));

        assertThat(productoRepository.findByNombreContainingIgnoreCase("teclado")).hasSize(1);
    }

    @Test
    void findByNombreAndEstadoFiltraCorrectamente() {
        productoRepository.saveAndFlush(producto("Mouse Pro", 0));

        assertThat(productoRepository.findByNombreAndEstado("Mouse Pro", Producto.EstadoProducto.AGOTADO))
                .isPresent();
        assertThat(productoRepository.findByNombreAndEstado("Mouse Pro", Producto.EstadoProducto.ACTIVO))
                .isEmpty();
    }

    @Test
    void findAllPaginadoTraeLaCategoriaAsociada() {
        productoRepository.saveAll(java.util.List.of(producto("P1", 1), producto("P2", 1), producto("P3", 1)));
        entityManager.flush();
        entityManager.clear();

        Page<Producto> pagina = productoRepository.findAll(
                PageRequest.of(0, 2, Sort.by("id").ascending()));

        assertThat(pagina.getTotalElements()).isEqualTo(3);
        assertThat(pagina.getContent()).hasSize(2);
        assertThat(pagina.getContent().get(0).getCategoria().getNombre()).isEqualTo("Computo");
    }

    @Test
    void categoriaRepositoryBuscaPorNombreIgnoreCase() {
        assertThat(categoriaRepository.findByNombreIgnoreCase("computo")).isPresent();
        assertThat(categoriaRepository.existsByNombreIgnoreCase("COMPUTO")).isTrue();
        assertThat(categoriaRepository.existsByNombreIgnoreCase("otra")).isFalse();
    }
}