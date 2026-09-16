package com.aosorio.ecommerce.catalogo.service;

import com.aosorio.ecommerce.catalogo.domain.Categoria;
import com.aosorio.ecommerce.catalogo.domain.Producto;
import com.aosorio.ecommerce.catalogo.dto.ProductoRequestDTO;
import com.aosorio.ecommerce.catalogo.dto.ProductoResponseDTO;
import com.aosorio.ecommerce.catalogo.exception.ResourceInUseException;
import com.aosorio.ecommerce.catalogo.mapper.ProductoMapper;
import com.aosorio.ecommerce.catalogo.repository.CategoriaRepository;
import com.aosorio.ecommerce.catalogo.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoServiceImplTest {

    @Mock
    private ProductoRepository productoRepository;
    @Mock
    private CategoriaRepository categoriaRepository;

    private ProductoMapper productoMapper;
    private ProductoServiceImpl productoService;

    @BeforeEach
    void setUp() {
        productoMapper = new ProductoMapper();
        productoService = new ProductoServiceImpl(productoRepository, categoriaRepository, productoMapper);
    }

    private Categoria categoria(Long id) {
        return Categoria.builder().id(id).nombre("Tecnología").descripcion("Electrónica").build();
    }

    private ProductoRequestDTO request(int cantidad) {
        return ProductoRequestDTO.builder()
                .nombre("Laptop Pro")
                .descripcion("Nueva")
                .precio(new BigDecimal("1599.99"))
                .stock(cantidad)
                .categoriaId(1L)
                .build();
    }

    private Producto producto(Long id, int stock, String estado) {
        return Producto.builder()
                .id(id)
                .nombre("Laptop Pro")
                .descripcion("Nueva")
                .precio(new BigDecimal("1599.99"))
                .stock(stock)
                .categoria(categoria(1L))
                .estado(Producto.EstadoProducto.valueOf(estado))
                .build();
    }

    @Test
    void crearGuardaProductoActivo() {
        when(productoRepository.existsByNombre("Laptop Pro")).thenReturn(false);
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria(1L)));
        when(productoRepository.save(any(Producto.class))).thenReturn(producto(1L, 5, "ACTIVO"));

        ProductoResponseDTO respuesta = productoService.crear(request(5));

        assertThat(respuesta.id()).isEqualTo(1L);
        assertThat(respuesta.nombre()).isEqualTo("Laptop Pro");
        assertThat(respuesta.estado()).isEqualTo("ACTIVO");
        assertThat(respuesta.categoria()).isEqualTo("Tecnología");
    }

    @Test
    void crearConNombreDuplicadoLanzaExcepcion() {
        when(productoRepository.existsByNombre("Laptop Pro")).thenReturn(true);

        assertThatThrownBy(() -> productoService.crear(request(5)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void crearConCategoriaInexistenteLanzaExcepcion() {
        when(productoRepository.existsByNombre("Laptop Pro")).thenReturn(false);
        when(categoriaRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productoService.crear(request(5)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void actualizarPasaEstadoAgotadoSiStockEsCero() {
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto(1L, 0, "AGOTADO")));
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria(1L)));
        when(productoRepository.save(any(Producto.class)))
                .thenAnswer(invocation0 -> invocation0.getArgument(0));

        ProductoResponseDTO respuesta = productoService.actualizar(1L, request(0));

        assertThat(respuesta.estado()).isEqualTo("AGOTADO");
    }

    @Test
    void descontarStockReduceCantidad() {
        when(productoRepository.descontarStock(1L, 2)).thenReturn(1);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto(1L, 3, "ACTIVO")));
        when(productoRepository.save(any(Producto.class)))
                .thenAnswer(invocation0 -> invocation0.getArgument(0));

        ProductoResponseDTO respuesta = productoService.descontarStock(1L, 2);

        verify(productoRepository).descontarStock(1L, 2);
        assertThat(respuesta.stock()).isEqualTo(3);
    }

    @Test
    void descontarStockSinCambiosLanzaResourceInUse() {
        when(productoRepository.descontarStock(1L, 99)).thenReturn(0);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto(1L, 1, "ACTIVO")));

        assertThatThrownBy(() -> productoService.descontarStock(1L, 99))
                .isInstanceOf(ResourceInUseException.class)
                .hasMessageContaining("Stock insuficiente");
    }

    @Test
    void descontarStockMarcaAgotadoSiLlegaACero() {
        when(productoRepository.descontarStock(1L, 5)).thenReturn(1);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto(1L, 0, "ACTIVO")));
        when(productoRepository.save(any(Producto.class)))
                .thenAnswer(invocation0 -> invocation0.getArgument(0));

        ProductoResponseDTO respuesta = productoService.descontarStock(1L, 5);

        assertThat(respuesta.estado()).isEqualTo("AGOTADO");
    }

    @Test
    void reponerStockReactivaProductoAgotado() {
        when(productoRepository.reponerStock(1L, 10)).thenReturn(1);
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto(1L, 10, "AGOTADO")));
        when(productoRepository.save(any(Producto.class)))
                .thenAnswer(invocation0 -> invocation0.getArgument(0));

        ProductoResponseDTO respuesta = productoService.reponerStock(1L, 10);

        assertThat(respuesta.estado()).isEqualTo("ACTIVO");
    }

    @Test
    void reponerStockConProductoInexistenteLanzaNotFound() {
        when(productoRepository.reponerStock(99L, 10)).thenReturn(0);

        assertThatThrownBy(() -> productoService.reponerStock(99L, 10))
                .isInstanceOf(RuntimeException.class);
    }
}