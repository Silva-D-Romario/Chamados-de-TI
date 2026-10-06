package br.com.romariosilva.chamados.config;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.romariosilva.chamados.ticket.domain.Ticket;
import br.com.romariosilva.chamados.ticket.domain.TicketHistory;
import br.com.romariosilva.chamados.ticket.domain.TicketHistoryAction;
import br.com.romariosilva.chamados.ticket.domain.TicketHistoryRepository;
import br.com.romariosilva.chamados.ticket.domain.TicketPriority;
import br.com.romariosilva.chamados.ticket.domain.TicketRepository;
import br.com.romariosilva.chamados.ticket.domain.TicketStatus;
import br.com.romariosilva.chamados.user.domain.User;
import br.com.romariosilva.chamados.user.domain.UserRepository;
import br.com.romariosilva.chamados.user.domain.UserRole;

@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
public class DemoDataBootstrap implements ApplicationRunner {

    public static final String REQUESTER_EMAIL = "solicitante@demo.local";
    public static final String TECHNICIAN_EMAIL = "tecnico@demo.local";

    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final TicketHistoryRepository historyRepository;
    private final PasswordEncoder passwordEncoder;
    private final String password;

    public DemoDataBootstrap(UserRepository userRepository, TicketRepository ticketRepository,
            TicketHistoryRepository historyRepository, PasswordEncoder passwordEncoder,
            @Value("${app.demo.password:}") String password) {
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
        this.historyRepository = historyRepository;
        this.passwordEncoder = passwordEncoder;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (password.length() < 8) {
            throw new IllegalStateException("APP_DEMO_PASSWORD deve ter pelo menos 8 caracteres");
        }

        User requester = findOrCreate("Solicitante Demo", REQUESTER_EMAIL, UserRole.SOLICITANTE);
        User technician = findOrCreate("Técnico Demo", TECHNICIAN_EMAIL, UserRole.TECNICO);
        Instant now = Instant.now();

        createTicketIfAbsent(requester, technician, "Notebook sem acesso à rede",
                "O equipamento conecta ao Wi-Fi, mas não acessa os sistemas internos.",
                TicketPriority.ALTA, "Rede", now.plusSeconds(2 * 60 * 60), TicketStatus.EM_ATENDIMENTO);
        createTicketIfAbsent(requester, null, "Instalação de editor de código",
                "Solicitação de instalação e configuração do ambiente de desenvolvimento.",
                TicketPriority.MEDIA, "Software", now.plusSeconds(20 * 60 * 60), TicketStatus.ABERTO);
        createTicketIfAbsent(requester, technician, "Acesso ao sistema interno",
                "Usuário precisa de liberação para consultar o portal corporativo.",
                TicketPriority.BAIXA, "Acesso e permissões", now.plusSeconds(36 * 60 * 60),
                TicketStatus.EM_TRIAGEM);
    }

    private User findOrCreate(String name, String email, UserRole role) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseGet(() -> userRepository.save(
                        new User(name, email, passwordEncoder.encode(password), role)));
    }

    private void createTicketIfAbsent(User requester, User technician, String title, String description,
            TicketPriority priority, String category, Instant dueAt, TicketStatus status) {
        if (ticketRepository.existsByRequesterEmailIgnoreCaseAndTitle(requester.getEmail(), title)) {
            return;
        }
        Ticket ticket = new Ticket(title, description, priority, category, requester, dueAt);
        if (technician != null) {
            ticket.assignTo(technician);
        }
        if (status != TicketStatus.ABERTO) {
            ticket.changeStatus(status);
        }
        ticketRepository.save(ticket);
        historyRepository.save(new TicketHistory(ticket, requester, TicketHistoryAction.CRIADO,
                null, TicketStatus.ABERTO, "Chamado de demonstração"));
        if (technician != null) {
            historyRepository.save(new TicketHistory(ticket, requester, TicketHistoryAction.ATRIBUIDO,
                    TicketStatus.ABERTO, TicketStatus.EM_TRIAGEM, "Atribuído ao técnico de demonstração"));
            if (status != TicketStatus.EM_TRIAGEM) {
                historyRepository.save(new TicketHistory(ticket, technician, TicketHistoryAction.STATUS_ALTERADO,
                        TicketStatus.EM_TRIAGEM, status, "Atendimento iniciado pelo técnico de demonstração"));
            }
        }
    }
}
