package com.aosorio.ecommerce.auth.dto;

public record AuthResponseDTO(
        Long userId,
        String email,
        String rol,
        String token
) {
}
