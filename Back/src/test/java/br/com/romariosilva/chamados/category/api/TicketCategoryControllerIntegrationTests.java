package br.com.romariosilva.chamados.category.api;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.ObjectMapper;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TicketCategoryControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCreatesAndDeactivatesCategoryWhileRequesterOnlyListsActiveOnes() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/categories")
                        .with(jwt().jwt(token -> token.subject("admin@example.com")
                                .claim("roles", List.of("ADMIN"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  Periféricos  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Periféricos"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        long categoryId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/v1/categories")
                        .with(jwt().jwt(token -> token.subject("user@example.com")
                                .claim("roles", List.of("SOLICITANTE")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Periféricos')]").exists());

        mockMvc.perform(patch("/api/v1/categories/{id}", categoryId)
                        .with(jwt().jwt(token -> token.subject("admin@example.com")
                                .claim("roles", List.of("ADMIN"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Periféricos\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/v1/categories")
                        .with(jwt().jwt(token -> token.subject("user@example.com")
                                .claim("roles", List.of("SOLICITANTE")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Periféricos')]").doesNotExist());
    }

    @Test
    void requesterCannotManageCategories() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                        .with(jwt().jwt(token -> token.subject("user@example.com")
                                .claim("roles", List.of("SOLICITANTE"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Categoria indevida\"}"))
                .andExpect(status().isForbidden());
    }
}
