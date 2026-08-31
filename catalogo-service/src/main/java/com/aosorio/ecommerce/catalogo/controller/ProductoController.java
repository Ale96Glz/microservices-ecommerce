package com.aosorio.ecommerce.catalogo.controller;

import com.aosorio.ecommerce.catalogo.dto.PageResponseDTO;
import com.aosorio.ecommerce.catalogo.dto.ProductoRequestDTO;
import com.aosorio.ecommerce.catalogo.dto.ProductoResponseDTO;
import com.aosorio.ecommerce.catalogo.security.GatewayAuth;
import com.aosorio.ecommerce.catalogo.service.ProductoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/producto")
public class ProductoController {
    private final ProductoService productoService;

    @PostMapping
    public ResponseEntity<ProductoResponseDTO> save(
            @Valid @RequestBody ProductoRequestDTO productoRequestDTO,
            HttpServletRequest request) {
        GatewayAuth.requireAdmin(request);
        ProductoResponseDTO creado = productoService.crear(productoRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @GetMapping
    public ResponseEntity<List<ProductoResponseDTO>> getAll() {
        return ResponseEntity.ok(productoService.obtenerTodos());
    }

    @GetMapping("/pageable")
    public ResponseEntity<PageResponseDTO<ProductoResponseDTO>> getPageable(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        var pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        return ResponseEntity.ok(productoService.obtenerTodosPaginado(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductoResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(productoService.obtenerPorId(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable Long id, HttpServletRequest request) {
        GatewayAuth.requireAdmin(request);
        productoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductoResponseDTO> updateById(
            @PathVariable Long id,
            @Valid @RequestBody ProductoRequestDTO productoRequestDTO,
            HttpServletRequest request) {
        GatewayAuth.requireAdmin(request);
        return ResponseEntity.ok(productoService.actualizar(id, productoRequestDTO));
    }

    @PutMapping("/{id}/stock")
    public ResponseEntity<ProductoResponseDTO> descontarStock(
            @PathVariable Long id,
            @RequestParam int cantidad,
            HttpServletRequest request) {
        GatewayAuth.requireUser(request);
        return ResponseEntity.ok(productoService.descontarStock(id, cantidad));
    }
}
