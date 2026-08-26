package com.aosorio.ecommerce.pagos.controller;

import com.aosorio.ecommerce.pagos.dto.PageResponseDTO;
import com.aosorio.ecommerce.pagos.dto.PagoRequestDTO;
import com.aosorio.ecommerce.pagos.dto.PagoResponseDTO;
import com.aosorio.ecommerce.pagos.security.GatewayAuth;
import com.aosorio.ecommerce.pagos.service.PagoService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/pago")
public class PagoController {

    private final PagoService pagoService;

    @PostMapping
    public ResponseEntity<PagoResponseDTO> procesar(
            @Valid @RequestBody PagoRequestDTO requestBody,
            HttpServletRequest request) {
        GatewayAuth.User user = GatewayAuth.requireUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pagoService.procesar(user.id(), requestBody));
    }

    @GetMapping
    public ResponseEntity<List<PagoResponseDTO>> getAll(HttpServletRequest request) {
        GatewayAuth.requireAdmin(request);
        return ResponseEntity.ok(pagoService.obtenerTodos());
    }

    @GetMapping("/pageable")
    public ResponseEntity<PageResponseDTO<PagoResponseDTO>> getPageable(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        GatewayAuth.requireAdmin(request);
        var pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        return ResponseEntity.ok(pagoService.obtenerTodosPaginado(pageable));
    }

    @GetMapping("/pedido/{pedidoId}")
    public ResponseEntity<PagoResponseDTO> getByPedido(
            @PathVariable Long pedidoId,
            HttpServletRequest request) {
        GatewayAuth.User user = GatewayAuth.requireUser(request);
        PagoResponseDTO pago = pagoService.obtenerPorPedido(pedidoId);
        GatewayAuth.requireSelfOrAdmin(user, pago.usuarioId());
        return ResponseEntity.ok(pago);
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<PagoResponseDTO>> getByUsuario(
            @PathVariable Long usuarioId,
            HttpServletRequest request) {
        GatewayAuth.User user = GatewayAuth.requireUser(request);
        GatewayAuth.requireSelfOrAdmin(user, usuarioId);
        return ResponseEntity.ok(pagoService.obtenerPorUsuario(usuarioId));
    }

    @GetMapping("/mios")
    public ResponseEntity<List<PagoResponseDTO>> getMine(HttpServletRequest request) {
        GatewayAuth.User user = GatewayAuth.requireUser(request);
        return ResponseEntity.ok(pagoService.obtenerPorUsuario(user.id()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PagoResponseDTO> getById(@PathVariable Long id, HttpServletRequest request) {
        GatewayAuth.User user = GatewayAuth.requireUser(request);
        PagoResponseDTO pago = pagoService.obtenerPorId(id);
        GatewayAuth.requireSelfOrAdmin(user, pago.usuarioId());
        return ResponseEntity.ok(pago);
    }
}
