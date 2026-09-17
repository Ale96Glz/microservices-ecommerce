package com.aosorio.ecommerce.catalogo;

import com.aosorio.ecommerce.catalogo.domain.Categoria;
import com.aosorio.ecommerce.catalogo.domain.Producto;
import com.aosorio.ecommerce.catalogo.repository.CategoriaRepository;
import com.aosorio.ecommerce.catalogo.repository.ProductoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogoControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private EntityManager entityManager;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private Categoria categoria;

    @BeforeEach
    void setUp() {
        categoria = categoriaRepository.save(Categoria.builder()
                .nombre("Tecnologia")
                .descripcion("Productos tecnologicos")
                .build());
    }

    private String token(Long userId, String rol) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", "usuario" + userId + "@example.com")
                .claim("rol", rol)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 3_600_000L))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private Producto persistirProducto(String nombre, BigDecimal precio, int stock) {
        return productoRepository.saveAndFlush(Producto.builder()
                .nombre(nombre)
                .descripcion("descripcion de " + nombre)
                .precio(precio)
                .stock(stock)
                .categoria(categoria)
                .estado(stock > 0 ? Producto.EstadoProducto.ACTIVO : Producto.EstadoProducto.AGOTADO)
                .build());
    }

    @Test
    void listarProductosEsPublicoSinToken() throws Exception {
        mockMvc.perform(get("/api/v1/producto"))
                .andExpect(status().isOk());
    }

    @Test
    void crearProductoSinTokenRetorna401() throws Exception {
        mockMvc.perform(post("/api/v1/producto")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nombre", "Laptop",
                                "descripcion", "Laptop 14 pulgadas",
                                "precio", 1500.50,
                                "stock", 5,
                                "categoriaId", categoria.getId()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void crearProductoConTokenDeUsuarioRetorna403() throws Exception {
        mockMvc.perform(post("/api/v1/producto")
                        .header("Authorization", "Bearer " + token(10L, "USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nombre", "Laptop",
                                "descripcion", "Laptop 14 pulgadas",
                                "precio", 1500.50,
                                "stock", 5,
                                "categoriaId", categoria.getId()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void crearProductoConAdminRetorna201YSePersiste() throws Exception {
        mockMvc.perform(post("/api/v1/producto")
                        .header("Authorization", "Bearer " + token(1L, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nombre", "Monitor 27",
                                "descripcion", "Monitor QHD",
                                "precio", 320.00,
                                "stock", 8,
                                "categoriaId", categoria.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nombre").value("Monitor 27"))
                .andExpect(jsonPath("$.stock").value(8))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.categoria").value("Tecnologia"));

        assertThat(productoRepository.existsByNombre("Monitor 27")).isTrue();
    }

    @Test
    void obtenerProductoInexistenteRetorna404() throws Exception {
        mockMvc.perform(get("/api/v1/producto/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void crearProductoConNombreDuplicadoRetorna409() throws Exception {
        persistirProducto("Duplicado", new BigDecimal("10.00"), 1);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(post("/api/v1/producto")
                        .header("Authorization", "Bearer " + token(1L, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nombre", "Duplicado",
                                "descripcion", "otra vez",
                                "precio", 12.00,
                                "stock", 2,
                                "categoriaId", categoria.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void crearProductoConCategoriaInexistenteRetorna404() throws Exception {
        mockMvc.perform(post("/api/v1/producto")
                        .header("Authorization", "Bearer " + token(1L, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "nombre", "Sin categoria",
                                "descripcion", "no existe categoria",
                                "precio", 15.00,
                                "stock", 1,
                                "categoriaId", 999999))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void descontarStockValidoActualizaElStock() throws Exception {
        Producto producto = persistirProducto("Teclado", new BigDecimal("45.00"), 10);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(put("/api/v1/producto/" + producto.getId() + "/stock")
                        .param("cantidad", "3")
                        .header("Authorization", "Bearer " + token(10L, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(7))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
    }

    @Test
    void descontarStockInsuficienteRetorna409() throws Exception {
        Producto producto = persistirProducto("Mouse", new BigDecimal("20.00"), 2);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(put("/api/v1/producto/" + producto.getId() + "/stock")
                        .param("cantidad", "5")
                        .header("Authorization", "Bearer " + token(10L, "USER")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void descontarTodoElStockDejaElProductoAgotado() throws Exception {
        Producto producto = persistirProducto("Webcam", new BigDecimal("60.00"), 4);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(put("/api/v1/producto/" + producto.getId() + "/stock")
                        .param("cantidad", "4")
                        .header("Authorization", "Bearer " + token(10L, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(0))
                .andExpect(jsonPath("$.estado").value("AGOTADO"));
    }

    @Test
    void reponerStockReactivaProductoAgotado() throws Exception {
        Producto producto = persistirProducto("Parlante", new BigDecimal("80.00"), 0);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(put("/api/v1/producto/" + producto.getId() + "/stock/reponer")
                        .param("cantidad", "6")
                        .header("Authorization", "Bearer " + token(10L, "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock").value(6))
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
    }

    @Test
    void reponerStockProductoInexistenteRetorna404() throws Exception {
        mockMvc.perform(put("/api/v1/producto/999999/stock/reponer")
                        .param("cantidad", "1")
                        .header("Authorization", "Bearer " + token(10L, "USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void eliminarProductoConAdminRetorna204() throws Exception {
        Producto producto = persistirProducto("Tablet", new BigDecimal("500.00"), 3);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(delete("/api/v1/producto/" + producto.getId())
                        .header("Authorization", "Bearer " + token(1L, "ADMIN")))
                .andExpect(status().isNoContent());

        assertThat(productoRepository.findById(producto.getId())).isEmpty();
    }

    @Test
    void eliminarProductoSinTokenRetorna401() throws Exception {
        mockMvc.perform(delete("/api/v1/producto/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crearCategoriaConAdminRetorna201YEsVisibleEnElListado() throws Exception {
        mockMvc.perform(post("/api/v1/categoria")
                        .header("Authorization", "Bearer " + token(1L, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombre", "Hogar", "descripcion", "Articulos para el hogar"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Hogar"));

        mockMvc.perform(get("/api/v1/categoria"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre=='Hogar')]").exists());
    }

    @Test
    void eliminarCategoriaConProductosAsociadosRetorna409() throws Exception {
        persistirProducto("Silla", new BigDecimal("120.00"), 5);
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(delete("/api/v1/categoria/" + categoria.getId())
                        .header("Authorization", "Bearer " + token(1L, "ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void eliminarCategoriaSinProductosRetorna204() throws Exception {
        Categoria vacia = categoriaRepository.save(Categoria.builder()
                .nombre("Sin productos")
                .descripcion("categoria vacia")
                .build());
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(delete("/api/v1/categoria/" + vacia.getId())
                        .header("Authorization", "Bearer " + token(1L, "ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void eliminarCategoriaInexistenteRetorna404() throws Exception {
        mockMvc.perform(delete("/api/v1/categoria/999999")
                        .header("Authorization", "Bearer " + token(1L, "ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void crearCategoriaSinTokenRetorna401() throws Exception {
        mockMvc.perform(post("/api/v1/categoria")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("nombre", "Juguetes", "descripcion", "Juguetes"))))
                .andExpect(status().isUnauthorized());
    }
}