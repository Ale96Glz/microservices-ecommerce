package com.aosorio.ecommerce.auth.config;

import com.aosorio.ecommerce.auth.domain.Usuario;
import com.aosorio.ecommerce.auth.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminInitRunnerTest {

    @Mock
    UsuarioRepository usuarioRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    ConfigurableApplicationContext applicationContext;

    @Test
    void rechazaPasswordDeLab() {
        AdminInitRunner runner = new AdminInitRunner(
                usuarioRepository, passwordEncoder, applicationContext);
        ReflectionTestUtils.setField(runner, "email", "admin@ecommerce.local");
        ReflectionTestUtils.setField(runner, "password", "Admin1234");

        assertThatThrownBy(runner::createIfAbsent)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("laboratorio");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void noPisaUnAdminExistente() {
        when(usuarioRepository.existsByEmail("ops@ecommerce.local")).thenReturn(true);
        AdminInitRunner runner = new AdminInitRunner(
                usuarioRepository, passwordEncoder, applicationContext);
        ReflectionTestUtils.setField(runner, "email", "ops@ecommerce.local");
        ReflectionTestUtils.setField(runner, "password", "otra-clave-segura");

        runner.createIfAbsent();

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void creaAdminSiNoExiste() {
        when(usuarioRepository.existsByEmail("ops@ecommerce.local")).thenReturn(false);
        when(passwordEncoder.encode("otra-clave-segura")).thenReturn("hash");
        AdminInitRunner runner = new AdminInitRunner(
                usuarioRepository, passwordEncoder, applicationContext);
        ReflectionTestUtils.setField(runner, "email", "ops@ecommerce.local");
        ReflectionTestUtils.setField(runner, "password", "otra-clave-segura");

        runner.createIfAbsent();

        verify(usuarioRepository).save(any(Usuario.class));
    }
}
