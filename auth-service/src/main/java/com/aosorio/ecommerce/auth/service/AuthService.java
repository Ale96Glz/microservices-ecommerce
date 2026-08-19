package com.aosorio.ecommerce.auth.service;

import com.aosorio.ecommerce.auth.dto.AuthResponseDTO;
import com.aosorio.ecommerce.auth.dto.LoginRequestDTO;
import com.aosorio.ecommerce.auth.dto.RegisterRequestDTO;

public interface AuthService {
    AuthResponseDTO registrar(RegisterRequestDTO request);

    AuthResponseDTO login(LoginRequestDTO request);
}
