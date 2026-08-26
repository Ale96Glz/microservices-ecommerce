package com.aosorio.ecommerce.auth.service;

import com.aosorio.ecommerce.auth.domain.Usuario;
import com.aosorio.ecommerce.auth.dto.AuthResponseDTO;
import com.aosorio.ecommerce.auth.dto.LoginRequestDTO;
import com.aosorio.ecommerce.auth.dto.RegisterRequestDTO;
import com.aosorio.ecommerce.auth.exception.InvalidCredentialsException;
import com.aosorio.ecommerce.auth.exception.ResourceInUseException;
import com.aosorio.ecommerce.auth.repository.UsuarioRepository;
import com.aosorio.ecommerce.auth.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    @Transactional
    public AuthResponseDTO registrar(RegisterRequestDTO request) {
        log.info("Iniciando registro de usuario: {}", request.getEmail());

        if (usuarioRepository.existsByEmail(request.getEmail())) {
            throw new ResourceInUseException("Ya existe un usuario con el email: " + request.getEmail());
        }

        Usuario usuario = Usuario.builder()
                .nombre(request.getNombre())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .rol(Usuario.RolUsuario.USER)
                .build();

        Usuario guardado = usuarioRepository.save(usuario);
        log.info("Se ha registrado el usuario: {}", guardado.getId());
        return toAuthResponse(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponseDTO login(LoginRequestDTO request) {
        Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Credenciales invalidas"));

        if (!passwordEncoder.matches(request.getPassword(), usuario.getPasswordHash())) {
            throw new InvalidCredentialsException("Credenciales invalidas");
        }

        log.info("Login exitoso para usuario: {}", usuario.getId());
        return toAuthResponse(usuario);
    }

    private AuthResponseDTO toAuthResponse(Usuario usuario) {
        String token = jwtService.generateToken(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getRol().name()
        );
        return new AuthResponseDTO(usuario.getId(), usuario.getEmail(), usuario.getRol().name(), token);
    }
}
