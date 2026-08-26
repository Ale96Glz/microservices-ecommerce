package com.aosorio.ecommerce.pedidos.controller;

import com.aosorio.ecommerce.pedidos.dto.PageResponseDTO;
import com.aosorio.ecommerce.pedidos.dto.PedidoRequestDTO;
import com.aosorio.ecommerce.pedidos.dto.PedidoResponseDTO;
import com.aosorio.ecommerce.pedidos.security.GatewayAuth;
import com.aosorio.ecommerce.pedidos.service.PedidoService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/pedido")
public class PedidoController {

    private final PedidoService pedidoService;

    @PostMapping
    public ResponseEntity<PedidoResponseDTO> save(
            @Valid @RequestBody PedidoRequestDTO pedidoRequestDTO,
            HttpServletRequest request) {
        GatewayAuth.User user = GatewayAuth.requireUser(request);
        PedidoResponseDTO creado = pedidoService.crear(user.id(), pedidoRequestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @GetMapping
    public ResponseEntity<List<PedidoResponseDTO>> getAll(HttpServletRequest request) {
        GatewayAuth.requireAdmin(request);
        return ResponseEntity.ok(pedidoService.obtenerTodos());
    }

    @GetMapping("/pageable")
    public ResponseEntity<PageResponseDTO<PedidoResponseDTO>> getPageable(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        GatewayAuth.requireAdmin(request);
        var pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        return ResponseEntity.ok(pedidoService.obtenerTodosPaginado(pageable));
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<PedidoResponseDTO>> getByUsuario(
            @PathVariable Long usuarioId,
            HttpServletRequest request) {
        GatewayAuth.User user = GatewayAuth.requireUser(request);
        GatewayAuth.requireSelfOrAdmin(user, usuarioId);
        return ResponseEntity.ok(pedidoService.obtenerPorUsuario(usuarioId));
    }

    @GetMapping("/mios")
    public ResponseEntity<List<PedidoResponseDTO>> getMine(HttpServletRequest request) {
        GatewayAuth.User user = GatewayAuth.requireUser(request);
        return ResponseEntity.ok(pedidoService.obtenerPorUsuario(user.id()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PedidoResponseDTO> getById(@PathVariable Long id, HttpServletRequest request) {
        GatewayAuth.User user = GatewayAuth.requireUser(request);
        PedidoResponseDTO pedido = pedidoService.obtenerPorId(id);
        GatewayAuth.requireSelfOrAdmin(user, pedido.usuarioId());
        return ResponseEntity.ok(pedido);
    }

    @PutMapping("/{id}/cancelar")
    public ResponseEntity<PedidoResponseDTO> cancelar(@PathVariable Long id, HttpServletRequest request) {
        GatewayAuth.User user = GatewayAuth.requireUser(request);
        PedidoResponseDTO pedido = pedidoService.obtenerPorId(id);
        GatewayAuth.requireSelfOrAdmin(user, pedido.usuarioId());
        return ResponseEntity.ok(pedidoService.cancelar(id));
    }
}
