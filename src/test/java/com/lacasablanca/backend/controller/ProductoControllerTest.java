package com.lacasablanca.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lacasablanca.backend.dto.LoginRequest;
import com.lacasablanca.backend.model.Categoria;
import com.lacasablanca.backend.repository.CategoriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoriaRepository categoriaRepository;

    private String tokenMozo;

    @BeforeEach
    void autenticar() throws Exception {
        LoginRequest req = new LoginRequest("mozo_01", "password123");
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(res.getResponse().getContentAsString());
        this.tokenMozo = json.get("token").asText();
    }

    @Test
    @DisplayName("PRB-21 E3: Listar todos los productos sin filtro retorna 200 y catálogo completo")
    void listarTodosLosProductosExitoso() throws Exception {
        mockMvc.perform(get("/api/v1/productos")
                .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].nombre").isNotEmpty())
                .andExpect(jsonPath("$[0].precio").isNumber())
                .andExpect(jsonPath("$[0].categoria_id").isNumber());
    }

    @Test
    @DisplayName("PRB-21 E4: Filtrar productos por categoria_id existente retorna 200 y solo los productos de esa categoría")
    void listarProductosFiltradosPorCategoria() throws Exception {
        Categoria cat = categoriaRepository.findAll().stream()
                .filter(c -> "Platos de Fondo".equals(c.getNombre()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get("/api/v1/productos")
                .param("categoria_id", String.valueOf(cat.getId()))
                .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("Ceviche Clásico"))
                .andExpect(jsonPath("$[0].categoria_id").value(cat.getId()));
    }

    @Test
    @DisplayName("PRB-21 E5: Filtrar productos por categoria_id inexistente retorna 200 y lista vacía")
    void listarProductosCategoriaInexistenteRetornaVacio() throws Exception {
        mockMvc.perform(get("/api/v1/productos")
                .param("categoria_id", "99999")
                .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("PRB-21 E6: Listar productos sin token JWT retorna 401 Unauthorized")
    void listarProductosSinTokenRetorna401() throws Exception {
        mockMvc.perform(get("/api/v1/productos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje").value("No autorizado: Token JWT requerido o inválido"));
    }
}
