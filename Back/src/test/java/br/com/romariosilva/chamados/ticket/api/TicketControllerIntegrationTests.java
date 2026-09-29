package br.com.romariosilva.chamados.ticket.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import br.com.romariosilva.chamados.ticket.domain.TicketRepository;
import br.com.romariosilva.chamados.user.domain.UserRepository;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TicketControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        ticketRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void requesterListsAndReadsOnlyOwnTickets() throws Exception {
        String firstToken = register("Primeiro Usuário", "first@example.com");
        String secondToken = register("Segundo Usuário", "second@example.com");
        long firstTicketId = createTicket(firstToken, "Notebook não liga");
        long secondTicketId = createTicket(secondToken, "Acesso ao sistema");

        mockMvc.perform(get("/api/v1/tickets").header(AUTHORIZATION, bearer(firstToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(firstTicketId))
                .andExpect(jsonPath("$.content[0].requester.fullName").value("Primeiro Usuário"));

        mockMvc.perform(get("/api/v1/tickets/{id}", secondTicketId)
                        .header(AUTHORIZATION, bearer(firstToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerUpdatesAndDeletesOpenTicket() throws Exception {
        String token = register("Romário Silva", "romario@example.com");
        long ticketId = createTicket(token, "Problema inicial");

        mockMvc.perform(put("/api/v1/tickets/{id}", ticketId)
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketJson("Título corrigido")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Título corrigido"))
                .andExpect(jsonPath("$.status").value("ABERTO"));

        mockMvc.perform(delete("/api/v1/tickets/{id}", ticketId)
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/tickets/{id}", ticketId)
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsTicketCreationWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/tickets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketJson("Sem autenticação")))
                .andExpect(status().isUnauthorized());
    }

    private String register(String name, String email) throws Exception {
        String body = """
                {"fullName":"%s","email":"%s","password":"senha-segura"}
                """.formatted(name, email);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private long createTicket(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tickets")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketJson(title)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ABERTO"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String ticketJson(String title) {
        return """
                {
                  "title":"%s",
                  "description":"Descrição detalhada do problema",
                  "priority":"MEDIA",
                  "category":"Hardware"
                }
                """.formatted(title);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
