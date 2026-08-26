package com.aosorio.ecommerce.notificaciones.controller;

import com.aosorio.ecommerce.notificaciones.dto.NotificacionRequestDTO;
import com.aosorio.ecommerce.notificaciones.dto.NotificacionResponseDTO;
import com.aosorio.ecommerce.notificaciones.dto.PageResponseDTO;
import com.aosorio.ecommerce.notificaciones.security.GatewayAuth;
import com.aosorio.ecommerce.notificaciones.service.NotificacionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/notificacion")
public class NotificacionController {

    private final NotificacionService notificacionService;

    @PostMapping
    public ResponseEntity<NotificacionResponseDTO> crear(
            @Valid @RequestBody NotificacionRequestDTO requestBody,
            HttpServletRequest request) {
        GatewayAuth.User user = GatewayAuth.requireUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(notificacionService.crear(user.id(), requestBody));
    }

    @GetMapping
    public ResponseEntity<List<NotificacionResponseDTO>> getAll(HttpServletRequest request) {
        GatewayAuth.requireAdmin(request);
        return ResponseEntity.ok(notificacionService.obtenerTodos());
    }

    @GetMapping("/pageable")
    public ResponseEntity<PageResponseDTO<NotificacionResponseDTO>> getPageable(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        GatewayAuth.requireAdmin(request);
        var pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        return ResponseEntity.ok(notificacionService.obtenerTodosPaginado(pageable));
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<NotificacionResponseDTO>> getByUsuario(
            @PathVariable Long usuarioId,
            HttpServletRequest request) {
        GatewayAuth.User user = GatewayAuth.requireUser(request);
        GatewayAuth.requireSelfOrAdmin(user, usuarioId);
        return ResponseEntity.ok(notificacionService.obtenerPorUsuario(usuarioId));
    }

    @GetMapping("/usuario/{usuarioId}/no-leidas")
    public ResponseEntity<List<NotificacionResponseDTO>> getNoLeidasByUsuario(
            @PathVariable Long usuarioId,
            HttpServletRequest request) {
        GatewayAuth.User user = GatewayAuth.requireUser(request);
        GatewayAuth.requireSelfOrAdmin(user, usuarioId);
        return ResponseEntity.ok(notificacionService.obtenerNoLeidasPorUsuario(usuarioId));
    }

    @GetMapping("/mias")
    public ResponseEntity<List<NotificacionResponseDTO>> getMine(HttpServletRequest request) {
        GatewayAuth.User user = GatewayAuth.requireUser(request);
        return ResponseEntity.ok(notificacionService.obtenerPorUsuario(user.id()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificacionResponseDTO> getById(
            @PathVariable Long id,
            HttpServletRequest request) {
        GatewayAuth.User user = GatewayAuth.requireUser(request);
        NotificacionResponseDTO notificacion = notificacionService.obtenerPorId(id);
        GatewayAuth.requireSelfOrAdmin(user, notificacion.usuarioId());
        return ResponseEntity.ok(notificacion);
    }

    @PatchMapping("/{id}/leida")
    public ResponseEntity<NotificacionResponseDTO> marcarComoLeida(
            @PathVariable Long id,
            HttpServletRequest request) {
        GatewayAuth.User user = GatewayAuth.requireUser(request);
        NotificacionResponseDTO notificacion = notificacionService.obtenerPorId(id);
        GatewayAuth.requireSelfOrAdmin(user, notificacion.usuarioId());
        return ResponseEntity.ok(notificacionService.marcarComoLeida(id));
    }
}
