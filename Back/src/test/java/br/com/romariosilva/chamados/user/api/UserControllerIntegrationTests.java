package br.com.romariosilva.chamados.user.api;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import br.com.romariosilva.chamados.ticket.domain.TicketRepository;
import br.com.romariosilva.chamados.ticket.domain.TicketHistoryRepository;
import br.com.romariosilva.chamados.ticket.domain.TicketCommentRepository;
import br.com.romariosilva.chamados.user.domain.User;
import br.com.romariosilva.chamados.user.domain.UserRepository;
import br.com.romariosilva.chamados.user.domain.UserRole;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TicketHistoryRepository historyRepository;

    @Autowired
    private TicketCommentRepository commentRepository;

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
    void adminPromotesRequesterToTechnician() throws Exception {
        User requester = userRepository.save(new User("Técnico Futuro", "tech@example.com",
                passwordEncoder.encode("senha-segura"), UserRole.SOLICITANTE));

        mockMvc.perform(patch("/api/v1/users/{id}/role", requester.getId())
                        .with(jwt().jwt(token -> token.subject("admin@example.com")
                                .claim("roles", List.of("ADMIN"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"TECNICO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TECNICO"));

        mockMvc.perform(get("/api/v1/users/technicians")
                        .with(jwt().jwt(token -> token.subject("admin@example.com")
                                .claim("roles", List.of("ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("tech@example.com"));
    }

    @Test
    void requesterCannotManageRoles() throws Exception {
        User requester = userRepository.save(new User("Usuário", "user@example.com",
                passwordEncoder.encode("senha-segura"), UserRole.SOLICITANTE));

        mockMvc.perform(patch("/api/v1/users/{id}/role", requester.getId())
                        .with(jwt().jwt(token -> token.subject("user@example.com")
                                .claim("roles", List.of("SOLICITANTE"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }
}
