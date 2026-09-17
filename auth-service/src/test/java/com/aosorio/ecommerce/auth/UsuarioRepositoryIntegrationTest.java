package com.aosorio.ecommerce.auth;

import com.aosorio.ecommerce.auth.domain.Usuario;
import com.aosorio.ecommerce.auth.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class UsuarioRepositoryIntegrationTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Usuario usuario(String email) {
        return Usuario.builder()
                .nombre("Ana Lopez")
                .email(email)
                .passwordHash("$2a$10$hashdeprueba")
                .rol(Usuario.RolUsuario.USER)
                .build();
    }

    @Test
    void guardaUsuarioYAsignaIdYFechaDeCreacion() {
        Usuario guardado = usuarioRepository.save(usuario("repo1@example.com"));

        assertThat(guardado.getId()).isNotNull();
        assertThat(guardado.getFechaCreacion()).isNotNull();
        assertThat(guardado.getRol()).isEqualTo(Usuario.RolUsuario.USER);
    }

    @Test
    void findByEmailRetornaElUsuarioGuardado() {
        usuarioRepository.save(usuario("repo2@example.com"));

        Optional<Usuario> encontrado = usuarioRepository.findByEmail("repo2@example.com");

        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getNombre()).isEqualTo("Ana Lopez");
    }

    @Test
    void findByEmailInexistenteRetornaVacio() {
        assertThat(usuarioRepository.findByEmail("nadie@example.com")).isEmpty();
    }

    @Test
    void existsByEmailReflejaElEstadoReal() {
        usuarioRepository.save(usuario("repo3@example.com"));

        assertThat(usuarioRepository.existsByEmail("repo3@example.com")).isTrue();
        assertThat(usuarioRepository.existsByEmail("otro@example.com")).isFalse();
    }

    @Test
    void existsByEmailAndIdNotExcluyeElPropioId() {
        Usuario guardado = usuarioRepository.save(usuario("repo4@example.com"));

        assertThat(usuarioRepository.existsByEmailAndIdNot("repo4@example.com", guardado.getId())).isFalse();
        assertThat(usuarioRepository.existsByEmailAndIdNot("repo4@example.com", guardado.getId() + 1)).isTrue();
    }

    @Test
    void emailDuplicadoViolaLaRestriccionUnica() {
        usuarioRepository.saveAndFlush(usuario("unico@example.com"));

        assertThatThrownBy(() -> usuarioRepository.saveAndFlush(usuario("unico@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void actualizarPasswordPersisteElCambio() {
        Usuario guardado = usuarioRepository.save(usuario("repo5@example.com"));
        guardado.setPasswordHash("$2a$10$nuevohash");
        usuarioRepository.saveAndFlush(guardado);

        Usuario recargado = usuarioRepository.findById(guardado.getId()).orElseThrow();
        assertThat(recargado.getPasswordHash()).isEqualTo("$2a$10$nuevohash");
        assertThat(recargado.getFechaModificacion()).isNotNull();
    }
}