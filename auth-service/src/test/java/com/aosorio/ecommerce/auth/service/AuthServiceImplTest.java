package com.aosorio.ecommerce.auth.service;

import com.aosorio.ecommerce.auth.domain.Usuario;
import com.aosorio.ecommerce.auth.dto.AuthResponseDTO;
import com.aosorio.ecommerce.auth.dto.LoginRequestDTO;
import com.aosorio.ecommerce.auth.dto.RegisterRequestDTO;
import com.aosorio.ecommerce.auth.exception.InvalidCredentialsException;
import com.aosorio.ecommerce.auth.exception.ResourceInUseException;
import com.aosorio.ecommerce.auth.repository.UsuarioRepository;
import com.aosorio.ecommerce.auth.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthServiceImpl authService;

    private static final String SECRET = "clave-secreta-suficientemente-larga-para-hmac-sha-256";

    @BeforeEach
    void setUp() {
        JwtService jwtService = new JwtService(SECRET, 3_600_000L);
        authService = new AuthServiceImpl(usuarioRepository, passwordEncoder, jwtService);
    }

    private RegisterRequestDTO requestRegistro(String email, String password) {
        return RegisterRequestDTO.builder()
                .nombre("Ana Lopez")
                .email(email)
                .password(password)
                .build();
    }

    private Usuario usuarioPersistido(Long id, String email, String passwordHash, String rol) {
        return Usuario.builder()
                .id(id)
                .nombre("Ana Lopez")
                .email(email)
                .passwordHash(passwordHash)
                .rol(Usuario.RolUsuario.valueOf(rol))
                .build();
    }

    @Test
    void registrarCreaUsuarioConRolUserYRetornaToken() {
        when(usuarioRepository.existsByEmail("ana@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("{bcrypt}hash");
        when(usuarioRepository.save(any(Usuario.class)))
                .thenReturn(usuarioPersistido(1L, "ana@example.com", "{bcrypt}hash", "USER"));

        AuthResponseDTO respuesta = authService.registrar(requestRegistro("ana@example.com", "password123"));

        assertThat(respuesta.userId()).isEqualTo(1L);
        assertThat(respuesta.email()).isEqualTo("ana@example.com");
        assertThat(respuesta.rol()).isEqualTo("USER");
        assertThat(respuesta.token()).isNotBlank();

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertThat(captor.getValue().getRol()).isEqualTo(Usuario.RolUsuario.USER);
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("{bcrypt}hash");
    }

    @Test
    void registrarConEmailExistenteLanzaResourceInUse() {
        when(usuarioRepository.existsByEmail("ana@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.registrar(requestRegistro("ana@example.com", "password123")))
                .isInstanceOf(ResourceInUseException.class)
                .hasMessageContaining("ana@example.com");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void loginExitosoRetornaToken() {
        when(usuarioRepository.findByEmail("ana@example.com"))
                .thenReturn(Optional.of(usuarioPersistido(7L, "ana@example.com", "{bcrypt}hash", "ADMIN")));
        when(passwordEncoder.matches("password123", "{bcrypt}hash")).thenReturn(true);

        AuthResponseDTO respuesta = authService.login(
                LoginRequestDTO.builder().email("ana@example.com").password("password123").build());

        assertThat(respuesta.userId()).isEqualTo(7L);
        assertThat(respuesta.rol()).isEqualTo("ADMIN");
        assertThat(respuesta.token()).isNotBlank();
    }

    @Test
    void loginConPasswordIncorrectaLanzaInvalidCredentials() {
        when(usuarioRepository.findByEmail("ana@example.com"))
                .thenReturn(Optional.of(usuarioPersistido(7L, "ana@example.com", "{bcrypt}hash", "USER")));
        when(passwordEncoder.matches("incorrecta", "{bcrypt}hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(
                LoginRequestDTO.builder().email("ana@example.com").password("incorrecta").build()))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginConEmailInexistenteLanzaInvalidCredentials() {
        when(usuarioRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(
                LoginRequestDTO.builder().email("ghost@example.com").password("password123").build()))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}