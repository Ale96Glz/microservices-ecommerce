package com.aosorio.ecommerce.auth.config;

import com.aosorio.ecommerce.auth.domain.Usuario;
import com.aosorio.ecommerce.auth.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
@ConditionalOnProperty(name = "auth.bootstrap-admin", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class AdminInitRunner implements ApplicationRunner {

    static final String INSECURE_LAB_PASSWORD = "Admin1234";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final ConfigurableApplicationContext applicationContext;

    @Value("${auth.admin.email:admin@ecommerce.local}")
    private String email;

    @Value("${auth.admin.password:}")
    private String password;

    @Value("${auth.bootstrap-admin-exit:false}")
    private boolean exitWhenDone;

    @Override
    public void run(ApplicationArguments args) {
        int code = 0;
        try {
            createIfAbsent();
        } catch (RuntimeException ex) {
            code = 1;
            log.error("No se pudo crear el ADMIN inicial: {}", ex.getMessage());
            if (!exitWhenDone) {
                throw ex;
            }
        }
        if (exitWhenDone) {
            final int status = code;
            SpringApplication.exit(applicationContext, () -> status);
            System.exit(status);
        }
    }

    void createIfAbsent() {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "AUTH_ADMIN_PASSWORD es obligatorio para crear el primer ADMIN");
        }
        if (INSECURE_LAB_PASSWORD.equals(password)) {
            throw new IllegalStateException(
                    "AUTH_ADMIN_PASSWORD no puede ser el password de laboratorio Admin1234");
        }
        if (usuarioRepository.existsByEmail(email)) {
            log.info("ADMIN {} ya existe; el Job no lo modifica", email);
            return;
        }
        Usuario admin = Usuario.builder()
                .nombre("Administrador")
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .rol(Usuario.RolUsuario.ADMIN)
                .build();
        usuarioRepository.save(admin);
        log.info("Usuario ADMIN inicial creado: {}", email);
    }
}
