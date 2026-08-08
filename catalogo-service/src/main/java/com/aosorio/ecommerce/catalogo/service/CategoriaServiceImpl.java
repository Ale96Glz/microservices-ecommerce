package com.aosorio.ecommerce.catalogo.service;

import com.aosorio.ecommerce.catalogo.domain.Categoria;
import com.aosorio.ecommerce.catalogo.dto.CategoriaRequestDTO;
import com.aosorio.ecommerce.catalogo.dto.CategoriaResponseDTO;
import com.aosorio.ecommerce.catalogo.mapper.CategoriaMapper;
import com.aosorio.ecommerce.catalogo.repository.CategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoriaServiceImpl implements CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final CategoriaMapper categoriaMapper;

    @Override
    @Transactional
    public CategoriaResponseDTO crear(CategoriaRequestDTO categoriaRequestDTO) {
        Categoria guardada = categoriaRepository.save(categoriaMapper.toEntity(categoriaRequestDTO));
        return categoriaMapper.toResponseDto(guardada);
    }

    @Override
    @Transactional
    public CategoriaResponseDTO actualizar(Long id, CategoriaRequestDTO categoriaRequestDTO) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("No se encontró la categoria con id: " + id));
        categoria.setNombre(categoriaRequestDTO.getNombre());
        categoria.setDescripcion(categoriaRequestDTO.getDescripcion());
        return categoriaMapper.toResponseDto(categoriaRepository.save(categoria));
    }

    @Override
    @Transactional
    public void eliminar(Long id) {
        if (!categoriaRepository.existsById(id)) {
            throw new RuntimeException("No se encontró la categoria con id: " + id);
        }
        categoriaRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoriaResponseDTO obtenerPorId(Long id) {
        return categoriaRepository.findById(id)
                .map(categoriaMapper::toResponseDto)
                .orElseThrow(() -> new RuntimeException("No se encontró la categoria con id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoriaResponseDTO> obtenerTodos() {
        return categoriaRepository.findAll().stream()
                .map(categoriaMapper::toResponseDto)
                .toList();
    }
}
