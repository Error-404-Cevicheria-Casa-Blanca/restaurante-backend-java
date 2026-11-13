package com.lacasablanca.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lacasablanca.backend.dto.CrearPedidoRequest;
import com.lacasablanca.backend.dto.ItemPedidoRequest;
import com.lacasablanca.backend.dto.LoginRequest;
import com.lacasablanca.backend.model.EstadoMesa;
import com.lacasablanca.backend.model.Mesa;
import com.lacasablanca.backend.repository.MesaRepository;
import com.lacasablanca.backend.repository.PedidoRepository;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PRB-87: PATCH /api/v1/pedidos/{pedidoId}/items/{itemId}/estado y campo estado por plato. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EstadoItemPedidoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MesaRepository mesaRepository;

    @Autowired
    private PedidoRepository pedidoRepository;

    private String tokenMozo;
    private String tokenCocinero;
    private String tokenCajero;

    @BeforeEach
    void setUp() throws Exception {
        pedidoRepository.deleteAll();
        for (Mesa m : mesaRepository.findAll()) {
            m.setEstado(EstadoMesa.LIBRE);
            m.setMozo(null);
            m.setFechaApertura(null);
            m.setFechaCierre(null);
            mesaRepository.save(m);
        }
        tokenMozo = login("mozo_01");
        tokenCocinero = login("cocinero_01");
        tokenCajero = login("cajero_01");
    }

    private String login(String username) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    /** Crea un pedido de 2 ítems en la mesa dada y devuelve [pedidoId, itemId1, itemId2]. */
    private int[] crearPedido(int mesaId, String clientRequestId) throws Exception {
        CrearPedidoRequest req = new CrearPedidoRequest(
                mesaId,
                List.of(new ItemPedidoRequest(1, 1, "Ceviche"), new ItemPedidoRequest(3, 1, "Bebida")),
                clientRequestId
        );
        MvcResult res = mockMvc.perform(post("/api/v1/pedidos")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(res.getResponse().getContentAsString());
        JsonNode items = node.get("items");
        return new int[]{node.get("id").asInt(), items.get(0).get("id").asInt(), items.get(1).get("id").asInt()};
    }

    private ResultActions patchEstado(String token, int pedidoId, int itemId, String estadoJson) throws Exception {
        return mockMvc.perform(patch("/api/v1/pedidos/" + pedidoId + "/items/" + itemId + "/estado")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\": " + estadoJson + "}"));
    }

    @Test
    @DisplayName("PRB-87: flujo completo PENDIENTE → EN_PREPARACION → LISTO → SERVIDO y GET refleja el estado por línea")
    void flujoCompletoPorPlato() throws Exception {
        int[] ids = crearPedido(5, "uuid-prb87-flujo");
        int pedidoId = ids[0];
        int item1 = ids[1];
        int item2 = ids[2];

        // Recién creado: ambos ítems PENDIENTE
        mockMvc.perform(get("/api/v1/pedidos").param("mesa_id", "5")
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].items", hasSize(2)))
                .andExpect(jsonPath("$[0].items[0].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$[0].items[1].estado").value("PENDIENTE"));

        patchEstado(tokenCocinero, pedidoId, item1, "\"EN_PREPARACION\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(item1))
                .andExpect(jsonPath("$.estado").value("EN_PREPARACION"));
        patchEstado(tokenCocinero, pedidoId, item1, "\"LISTO\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("LISTO"));
        patchEstado(tokenMozo, pedidoId, item1, "\"SERVIDO\"")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SERVIDO"));

        // Solo el ítem 1 avanzó; el 2 sigue PENDIENTE
        mockMvc.perform(get("/api/v1/pedidos").param("mesa_id", "5")
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].items[?(@.id==" + item1 + ")].estado").value("SERVIDO"))
                .andExpect(jsonPath("$[0].items[?(@.id==" + item2 + ")].estado").value("PENDIENTE"));

        mockMvc.perform(get("/api/v1/pedidos/" + pedidoId)
                        .header("Authorization", "Bearer " + tokenMozo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.id==" + item1 + ")].estado").value("SERVIDO"));
    }

    @Test
    @DisplayName("PRB-87: saltos, retrocesos y repeticiones devuelven 400 con mensaje claro")
    void transicionesInvalidasDevuelven400() throws Exception {
        int[] ids = crearPedido(6, "uuid-prb87-invalidas");
        int pedidoId = ids[0];
        int item = ids[1];

        // Salto PENDIENTE → LISTO
        patchEstado(tokenCocinero, pedidoId, item, "\"LISTO\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("PENDIENTE")))
                .andExpect(jsonPath("$.mensaje").value(containsString("EN_PREPARACION")));
        // Salto PENDIENTE → SERVIDO
        patchEstado(tokenCocinero, pedidoId, item, "\"SERVIDO\"").andExpect(status().isBadRequest());
        // Repetir el mismo estado
        patchEstado(tokenCocinero, pedidoId, item, "\"PENDIENTE\"").andExpect(status().isBadRequest());

        patchEstado(tokenCocinero, pedidoId, item, "\"EN_PREPARACION\"").andExpect(status().isOk());
        // Retroceso EN_PREPARACION → PENDIENTE
        patchEstado(tokenCocinero, pedidoId, item, "\"PENDIENTE\"").andExpect(status().isBadRequest());

        patchEstado(tokenCocinero, pedidoId, item, "\"LISTO\"").andExpect(status().isOk());
        patchEstado(tokenCocinero, pedidoId, item, "\"SERVIDO\"").andExpect(status().isOk());
        // Desde el estado final no hay más transiciones
        patchEstado(tokenCocinero, pedidoId, item, "\"LISTO\"").andExpect(status().isBadRequest());
        patchEstado(tokenCocinero, pedidoId, item, "\"SERVIDO\"")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("estado final")));
    }

    @Test
    @DisplayName("PRB-87: estado desconocido o ausente devuelve 400")
    void estadoDesconocidoOAusenteDevuelve400() throws Exception {
        int[] ids = crearPedido(7, "uuid-prb87-body");

        patchEstado(tokenCocinero, ids[0], ids[1], "\"HERVIDO\"").andExpect(status().isBadRequest());
        patchEstado(tokenCocinero, ids[0], ids[1], "null").andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/pedidos/" + ids[0] + "/items/" + ids[1] + "/estado")
                        .header("Authorization", "Bearer " + tokenCocinero)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PRB-87: ítem o pedido inexistente, o ítem de otro pedido, devuelve 404")
    void itemInexistenteOFueraDelPedidoDevuelve404() throws Exception {
        int[] a = crearPedido(8, "uuid-prb87-404-a");
        int[] b = crearPedido(9, "uuid-prb87-404-b");

        patchEstado(tokenCocinero, a[0], 999999, "\"EN_PREPARACION\"").andExpect(status().isNotFound());
        patchEstado(tokenCocinero, 999999, a[1], "\"EN_PREPARACION\"").andExpect(status().isNotFound());
        // Ítem de b usado con el pedido a
        patchEstado(tokenCocinero, a[0], b[1], "\"EN_PREPARACION\"").andExpect(status().isNotFound());

        // El ítem de b no cambió
        mockMvc.perform(get("/api/v1/pedidos/" + b[0]).header("Authorization", "Bearer " + tokenMozo))
                .andExpect(jsonPath("$.items[?(@.id==" + b[1] + ")].estado").value("PENDIENTE"));
    }

    @Test
    @DisplayName("PRB-87: no se puede cambiar el estado de ítems de un pedido ANULADO (409) y CAJERO no tiene permiso (403)")
    void pedidoAnuladoDevuelve409YRolSinPermisoDevuelve403() throws Exception {
        int[] ids = crearPedido(10, "uuid-prb87-409");

        patchEstado(tokenCajero, ids[0], ids[1], "\"EN_PREPARACION\"").andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/pedidos/" + ids[0] + "/anular")
                        .header("Authorization", "Bearer " + tokenMozo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"Cliente se retiró\"}"))
                .andExpect(status().isOk());

        patchEstado(tokenCocinero, ids[0], ids[1], "\"EN_PREPARACION\"").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PRB-87: sin token devuelve 401")
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(patch("/api/v1/pedidos/1/items/1/estado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\": \"EN_PREPARACION\"}"))
                .andExpect(status().isUnauthorized());
    }
}
