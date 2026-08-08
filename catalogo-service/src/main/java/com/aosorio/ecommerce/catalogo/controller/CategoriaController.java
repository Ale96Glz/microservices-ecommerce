package com.aosorio.ecommerce.catalogo.controller;

import com.aosorio.ecommerce.catalogo.domain.Categoria;
import com.aosorio.ecommerce.catalogo.dto.CategoriaRequestDTO;
import com.aosorio.ecommerce.catalogo.dto.CategoriaResponseDTO;
import com.aosorio.ecommerce.catalogo.service.CategoriaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/categoria")
public class CategoriaController {
    private final CategoriaService categoriaService;

    @PostMapping
    public ResponseEntity<CategoriaResponseDTO> save(
           @Valid @RequestBody CategoriaRequestDTO categoriaDTO) {

        CategoriaResponseDTO creado = categoriaService.crear(categoriaDTO);

        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @GetMapping
    public ResponseEntity<List<CategoriaResponseDTO>> findAll() {
        return ResponseEntity.ok(categoriaService.obtenerTodos());
    }


    @PutMapping("/{id}")
    public ResponseEntity<CategoriaResponseDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody CategoriaRequestDTO categoriaDTO)
    {
        CategoriaResponseDTO actualizado = categoriaService.actualizar(id, categoriaDTO);
        return ResponseEntity.ok(actualizado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id
    ){
        categoriaService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
