package com.aosorio.ecommerce.auth.config;

import com.aosorio.ecommerce.auth.domain.Usuario;
import com.aosorio.ecommerce.auth.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        String email = "admin@ecommerce.local";
        if (usuarioRepository.existsByEmail(email)) {
            return;
        }

        Usuario admin = Usuario.builder()
                .nombre("Administrador")
                .email(email)
                .passwordHash(passwordEncoder.encode("Admin1234"))
                .rol(Usuario.RolUsuario.ADMIN)
                .build();
        usuarioRepository.save(admin);
        log.info("Usuario ADMIN de demo creado: {} / Admin1234", email);
    }
}
