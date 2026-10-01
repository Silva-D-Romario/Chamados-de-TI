package br.com.romariosilva.chamados.ticket.api;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import br.com.romariosilva.chamados.ticket.domain.TicketRepository;
import br.com.romariosilva.chamados.ticket.domain.TicketHistoryRepository;
import br.com.romariosilva.chamados.ticket.domain.TicketCommentRepository;
import br.com.romariosilva.chamados.user.domain.User;
import br.com.romariosilva.chamados.user.domain.UserRepository;
import br.com.romariosilva.chamados.user.domain.UserRole;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    private TicketHistoryRepository historyRepository;

    @Autowired
    private TicketCommentRepository commentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        commentRepository.deleteAll();
        historyRepository.deleteAll();
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

    @Test
    void adminAssignsAndTechnicianMovesTicketWithHistory() throws Exception {
        String requesterToken = register("Solicitante", "requester@example.com");
        long ticketId = createTicket(requesterToken, "Configurar estação");
        User admin = userRepository.save(new User("Administrador", "admin@example.com",
                passwordEncoder.encode("senha-segura"), UserRole.ADMIN));
        User technician = userRepository.save(new User("Técnico", "tech@example.com",
                passwordEncoder.encode("senha-segura"), UserRole.TECNICO));

        mockMvc.perform(patch("/api/v1/tickets/{id}/assignment", ticketId)
                        .with(jwt().jwt(token -> token.subject(admin.getEmail()).claim("roles", List.of("ADMIN"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"technicianId\":" + technician.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technician.id").value(technician.getId()))
                .andExpect(jsonPath("$.status").value("EM_TRIAGEM"));

        mockMvc.perform(patch("/api/v1/tickets/{id}/status", ticketId)
                        .with(jwt().jwt(token -> token.subject(technician.getEmail())
                                .claim("roles", List.of("TECNICO"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_ATENDIMENTO\",\"note\":\"Atendimento iniciado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EM_ATENDIMENTO"));

        mockMvc.perform(get("/api/v1/tickets/{id}/history", ticketId)
                        .header(AUTHORIZATION, bearer(requesterToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].action").value("CRIADO"))
                .andExpect(jsonPath("$[1].action").value("ATRIBUIDO"))
                .andExpect(jsonPath("$[2].note").value("Atendimento iniciado"));
    }

    @Test
    void commentsRespectTicketVisibilityAndInternalAccess() throws Exception {
        String requesterToken = register("Solicitante", "requester@example.com");
        String otherToken = register("Outro Usuário", "other@example.com");
        long ticketId = createTicket(requesterToken, "Instalar certificado");
        User technician = userRepository.save(new User("Técnico", "tech@example.com",
                passwordEncoder.encode("senha-segura"), UserRole.TECNICO));

        mockMvc.perform(post("/api/v1/tickets/{id}/comments", ticketId)
                        .header(AUTHORIZATION, bearer(requesterToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Preciso acessar hoje\",\"internal\":false}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author.fullName").value("Solicitante"));

        mockMvc.perform(post("/api/v1/tickets/{id}/comments", ticketId)
                        .with(jwt().jwt(token -> token.subject(technician.getEmail())
                                .claim("roles", List.of("TECNICO"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Validar com segurança\",\"internal\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.internal").value(true));

        mockMvc.perform(get("/api/v1/tickets/{id}/comments", ticketId)
                        .header(AUTHORIZATION, bearer(requesterToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get("/api/v1/tickets/{id}/comments", ticketId)
                        .with(jwt().jwt(token -> token.subject(technician.getEmail())
                                .claim("roles", List.of("TECNICO")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/v1/tickets/{id}/comments", ticketId)
                        .header(AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void requesterCannotCreateInternalCommentAndTicketIncludesSla() throws Exception {
        String token = register("Solicitante", "requester@example.com");
        long ticketId = createTicket(token, "Configurar VPN");

        mockMvc.perform(get("/api/v1/tickets/{id}", ticketId)
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueAt").isNotEmpty())
                .andExpect(jsonPath("$.slaStatus").value("NO_PRAZO"));

        mockMvc.perform(post("/api/v1/tickets/{id}/comments", ticketId)
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Comentário restrito\",\"internal\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void filtersPaginateAndSummarizeOnlyVisibleTickets() throws Exception {
        String token = register("Solicitante", "requester@example.com");
        String otherToken = register("Outro", "other@example.com");
        createTicket(token, "Notebook sem imagem", "MEDIA", "Hardware");
        createTicket(token, "Instalar editor", "ALTA", "Software");
        createTicket(otherToken, "Notebook de outro usuário", "MEDIA", "Hardware");

        mockMvc.perform(get("/api/v1/tickets")
                        .header(AUTHORIZATION, bearer(token))
                        .param("q", "notebook")
                        .param("priority", "MEDIA")
                        .param("category", "Hardware")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Notebook sem imagem"));

        mockMvc.perform(get("/api/v1/tickets/summary")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.open").value(2))
                .andExpect(jsonPath("$.inProgress").value(0))
                .andExpect(jsonPath("$.resolved").value(0))
                .andExpect(jsonPath("$.overdue").value(0));

        mockMvc.perform(get("/api/v1/tickets/summary")
                        .header(AUTHORIZATION, bearer(token))
                        .param("priority", "ALTA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
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
        return createTicket(token, title, "MEDIA", "Hardware");
    }

    private long createTicket(String token, String title, String priority, String category) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/tickets")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketJson(title, priority, category)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ABERTO"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String ticketJson(String title) {
        return ticketJson(title, "MEDIA", "Hardware");
    }

    private String ticketJson(String title, String priority, String category) {
        return """
                {
                  "title":"%s",
                  "description":"Descrição detalhada do problema",
                  "priority":"%s",
                  "category":"%s"
                }
                """.formatted(title, priority, category);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
