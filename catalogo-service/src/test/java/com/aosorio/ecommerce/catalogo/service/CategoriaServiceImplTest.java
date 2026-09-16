package com.aosorio.ecommerce.catalogo.service;

import com.aosorio.ecommerce.catalogo.domain.Categoria;
import com.aosorio.ecommerce.catalogo.dto.CategoriaRequestDTO;
import com.aosorio.ecommerce.catalogo.dto.CategoriaResponseDTO;
import com.aosorio.ecommerce.catalogo.exception.ResourceInUseException;
import com.aosorio.ecommerce.catalogo.exception.ResourceNotFoundException;
import com.aosorio.ecommerce.catalogo.mapper.CategoriaMapper;
import com.aosorio.ecommerce.catalogo.repository.CategoriaRepository;
import com.aosorio.ecommerce.catalogo.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceImplTest {

    @Mock
    private CategoriaRepository categoriaRepository;
    @Mock
    private ProductoRepository productoRepository;

    private CategoriaMapper categoriaMapper;
    private CategoriaServiceImpl categoriaService;

    @BeforeEach
    void setUp() {
        categoriaMapper = new CategoriaMapper();
        categoriaService = new CategoriaServiceImpl(categoriaRepository, productoRepository, categoriaMapper);
    }

    private Categoria categoria(Long id) {
        return Categoria.builder().id(id).nombre("Ropa").descripcion("Vestuario").build();
    }

    private CategoriaRequestDTO request() {
        return CategoriaRequestDTO.builder().nombre("Ropa").descripcion("Vestuario").build();
    }

    @Test
    void crearGuardaYDevuelveLaCategoria() {
        when(categoriaRepository.save(any(Categoria.class))).thenReturn(categoria(1L));

        CategoriaResponseDTO respuesta = categoriaService.crear(request());

        assertThat(respuesta.id()).isEqualTo(1L);
        assertThat(respuesta.nombre()).isEqualTo("Ropa");
    }

    @Test
    void actualizarModificaNombreYDescripcion() {
        when(categoriaRepository.findById(1L)).thenReturn(Optional.of(categoria(1L)));
        when(categoriaRepository.save(any(Categoria.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoriaResponseDTO respuesta = categoriaService.actualizar(1L, request());

        assertThat(respuesta.nombre()).isEqualTo("Ropa");
    }

    @Test
    void actualizarInexistenteLanzaNotFound() {
        when(categoriaRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoriaService.actualizar(99L, request()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void eliminarSinProductosAsociadosBorra() {
        when(categoriaRepository.existsById(1L)).thenReturn(true);
        when(productoRepository.countByCategoriaId(1L)).thenReturn(0L);

        categoriaService.eliminar(1L);

        verify(categoriaRepository).deleteById(1L);
    }

    @Test
    void eliminarConProductosAsociadosLanzaResourceInUse() {
        when(categoriaRepository.existsById(1L)).thenReturn(true);
        when(productoRepository.countByCategoriaId(1L)).thenReturn(3L);

        assertThatThrownBy(() -> categoriaService.eliminar(1L))
                .isInstanceOf(ResourceInUseException.class)
                .hasMessageContaining("3 producto(s)");
    }

    @Test
    void eliminarInexistenteLanzaNotFound() {
        when(categoriaRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> categoriaService.eliminar(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void obtenerTodosDevuelveListaMapeada() {
        when(categoriaRepository.findAll()).thenReturn(List.of(categoria(1L), categoria(2L)));

        List<CategoriaResponseDTO> respuesta = categoriaService.obtenerTodos();

        assertThat(respuesta).hasSize(2);
        assertThat(respuesta.get(0).nombre()).isEqualTo("Ropa");
    }
}