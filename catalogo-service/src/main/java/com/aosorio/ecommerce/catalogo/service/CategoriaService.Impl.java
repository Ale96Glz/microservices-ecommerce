package com.aosorio.ecommerce.catalogo.service;

public class CategoriaServiceImpl implements CategoriaService {

    private final CategoriaRepository categoriaRepository;
    private final CategoriaMapper categoriaMapper;

    public CategoriaServiceImpl(CategoriaRepository categoriaRepository, CategoriaMapper categoriaMapper) {
        this.categoriaRepository = categoriaRepository;
        this.categoriaMapper = categoriaMapper;
    }

    @Override
    public CategoriaResponseDTO crear(CategoriaRequestDTO categoriaRequestDTO) {
        return categoriaMapper.toResponseDto(categoriaRepository.save(categoriaMapper.toEntity(categoriaRequestDTO)));
    }
}