package com.aosorio.ecommerce.auth;

import com.aosorio.ecommerce.auth.domain.Usuario;
import com.aosorio.ecommerce.auth.repository.UsuarioRepository;
import com.aosorio.ecommerce.auth.security.JwtService;
import com.aosorio.ecommerce.security.JwtValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtValidator jwtValidator;

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    @Test
    void registrarUsuarioRetorna201ConTokenYPersistePasswordHasheado() throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nombre", "Ana Lopez",
                                "email", "ana.integracion@example.com",
                                "password", "Password123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ana.integracion@example.com"))
                .andExpect(jsonPath("$.rol").value("USER"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        Long userId = objectMapper.readTree(respuesta).get("userId").asLong();
        Usuario guardado = usuarioRepository.findById(userId).orElseThrow();
        assertThat(guardado.getPasswordHash()).isNotEqualTo("Password123");
        assertThat(guardado.getPasswordHash()).startsWith("$2");
    }

    @Test
    void registrarConEmailDuplicadoRetorna409() throws Exception {
        Map<String, Object> body = Map.of(
                "nombre", "Ana Lopez",
                "email", "duplicado@example.com",
                "password", "Password123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void registrarConEmailInvalidoRetorna400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nombre", "Ana Lopez",
                                "email", "no-es-un-email",
                                "password", "Password123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void registrarConPasswordCortaRetorna400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nombre", "Ana Lopez",
                                "email", "corta@example.com",
                                "password", "123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("contraseña")));
    }

    @Test
    void loginConCredencialesValidasRetorna200ConTokenValido() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nombre", "Ana Lopez",
                                "email", "login.ok@example.com",
                                "password", "Password123"))))
                .andExpect(status().isCreated());

        String respuesta = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "login.ok@example.com",
                                "password", "Password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("login.ok@example.com"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(respuesta).get("token").asText();
        Claims claims = jwtValidator.validate(token);
        assertThat(claims.get("email")).isEqualTo("login.ok@example.com");
        assertThat(claims.get("rol")).isEqualTo("USER");
    }

    @Test
    void loginConPasswordIncorrectaRetorna401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nombre", "Ana Lopez",
                                "email", "login.mal@example.com",
                                "password", "Password123"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "login.mal@example.com",
                                "password", "OtraPassword123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void loginConEmailInexistenteRetorna401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", "noexiste@example.com",
                                "password", "Password123"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void endpointProtegidoSinTokenRetorna401() throws Exception {
        mockMvc.perform(get("/api/v1/usuario"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void endpointProtegidoConTokenAdminRetorna200() throws Exception {
        String token = jwtService.generateToken(1L, "admin@example.com", "ADMIN");

        mockMvc.perform(get("/api/v1/usuario")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void endpointAdminConTokenDeUsuarioRetorna403() throws Exception {
        String token = jwtService.generateToken(2L, "user@example.com", "USER");

        mockMvc.perform(get("/api/v1/usuario")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void usuarioPuedeConsultarSuPropioRecursoPeroNoElDeOtro() throws Exception {
        Long propioId = objectMapper.readTree(
                        mockMvc.perform(post("/api/v1/auth/register")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json(Map.of(
                                                "nombre", "Ana Lopez",
                                                "email", "propio@example.com",
                                                "password", "Password123"))))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString())
                .get("userId").asLong();

        String token = jwtService.generateToken(propioId, "propio@example.com", "USER");

        mockMvc.perform(get("/api/v1/usuario/" + propioId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("propio@example.com"));

        mockMvc.perform(get("/api/v1/usuario/" + (propioId + 999))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenFirmadoConOtroSecretoEsRechazado() throws Exception {
        JwtService otroEmisor = new JwtService("otro-secreto-completamente-distinto-de-32-caracteres", 3_600_000L);
        String tokenAjeno = otroEmisor.generateToken(1L, "intruso@example.com", "ADMIN");

        mockMvc.perform(get("/api/v1/usuario")
                        .header("Authorization", "Bearer " + tokenAjeno))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("expirado")));
    }
}