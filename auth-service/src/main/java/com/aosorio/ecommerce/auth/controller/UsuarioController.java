package com.aosorio.ecommerce.auth.controller;

import com.aosorio.ecommerce.auth.dto.PageResponseDTO;
import com.aosorio.ecommerce.auth.dto.UsuarioRequestDTO;
import com.aosorio.ecommerce.auth.dto.UsuarioResponseDTO;
import com.aosorio.ecommerce.auth.security.GatewayAuth;
import com.aosorio.ecommerce.auth.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/usuario")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping
    public ResponseEntity<List<UsuarioResponseDTO>> getAll(HttpServletRequest request) {
        GatewayAuth.requireAdmin(request);
        return ResponseEntity.ok(usuarioService.obtenerTodos());
    }

    @GetMapping("/pageable")
    public ResponseEntity<PageResponseDTO<UsuarioResponseDTO>> getPageable(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        GatewayAuth.requireAdmin(request);
        var pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        return ResponseEntity.ok(usuarioService.obtenerTodosPaginado(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponseDTO> getById(@PathVariable Long id, HttpServletRequest request) {
        GatewayAuth.User user = GatewayAuth.requireUser(request);
        GatewayAuth.requireSelfOrAdmin(user, id);
        return ResponseEntity.ok(usuarioService.obtenerPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponseDTO> updateById(
            @PathVariable Long id,
            @Valid @RequestBody UsuarioRequestDTO requestBody,
            HttpServletRequest request) {
        GatewayAuth.User user = GatewayAuth.requireUser(request);
        GatewayAuth.requireSelfOrAdmin(user, id);
        return ResponseEntity.ok(usuarioService.actualizar(id, requestBody));
    }

    @PutMapping("/{id}/rol")
    public ResponseEntity<UsuarioResponseDTO> updateRol(
            @PathVariable Long id,
            @RequestParam String rol,
            HttpServletRequest request) {
        GatewayAuth.requireAdmin(request);
        return ResponseEntity.ok(usuarioService.actualizarRol(id, rol));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable Long id, HttpServletRequest request) {
        GatewayAuth.requireAdmin(request);
        usuarioService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
