package br.com.romariosilva.chamados.ticket.application;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.romariosilva.chamados.ticket.domain.Ticket;
import br.com.romariosilva.chamados.ticket.domain.TicketPriority;
import br.com.romariosilva.chamados.ticket.domain.TicketRepository;
import br.com.romariosilva.chamados.ticket.domain.TicketStatus;
import br.com.romariosilva.chamados.user.domain.User;
import br.com.romariosilva.chamados.user.domain.UserRepository;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;

    public TicketService(TicketRepository ticketRepository, UserRepository userRepository) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TicketResponse create(Jwt jwt, TicketData data) {
        User requester = currentUser(jwt);
        Ticket ticket = new Ticket(data.title().trim(), data.description().trim(), data.priority(),
                data.category().trim(), requester);
        return TicketResponse.from(ticketRepository.save(ticket));
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> list(Jwt jwt, Pageable pageable) {
        Page<Ticket> tickets = hasSupportAccess(jwt)
                ? ticketRepository.findAll(pageable)
                : ticketRepository.findAllByRequesterEmailIgnoreCase(jwt.getSubject(), pageable);
        return PageResponse.from(tickets.map(TicketResponse::from));
    }

    @Transactional(readOnly = true)
    public TicketResponse findById(Jwt jwt, Long id) {
        return TicketResponse.from(visibleTicket(jwt, id));
    }

    @Transactional
    public TicketResponse update(Jwt jwt, Long id, TicketData data) {
        Ticket ticket = ownedOpenTicket(jwt, id);
        ticket.updateDetails(data.title().trim(), data.description().trim(), data.priority(), data.category().trim());
        return TicketResponse.from(ticket);
    }

    @Transactional
    public void delete(Jwt jwt, Long id) {
        ticketRepository.delete(ownedOpenTicket(jwt, id));
    }

    private User currentUser(Jwt jwt) {
        return userRepository.findByEmailIgnoreCase(jwt.getSubject())
                .filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Usuário não encontrado"));
    }

    private Ticket visibleTicket(Jwt jwt, Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Chamado não encontrado"));
        if (!hasSupportAccess(jwt) && !isOwner(jwt, ticket)) {
            throw new ResponseStatusException(NOT_FOUND, "Chamado não encontrado");
        }
        return ticket;
    }

    private Ticket ownedOpenTicket(Jwt jwt, Long id) {
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Chamado não encontrado"));
        if (!isOwner(jwt, ticket)) {
            throw new ResponseStatusException(FORBIDDEN, "Somente o solicitante pode alterar este chamado");
        }
        if (ticket.getStatus() != TicketStatus.ABERTO) {
            throw new ResponseStatusException(CONFLICT, "Somente chamados abertos podem ser alterados");
        }
        return ticket;
    }

    private boolean isOwner(Jwt jwt, Ticket ticket) {
        return ticket.getRequester().getEmail().equalsIgnoreCase(jwt.getSubject());
    }

    private boolean hasSupportAccess(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && (roles.contains("TECNICO") || roles.contains("ADMIN"));
    }

    public record TicketData(String title, String description, TicketPriority priority, String category) {
    }

    public record TicketResponse(
            Long id,
            String title,
            String description,
            TicketStatus status,
            TicketPriority priority,
            String category,
            UserSummary requester,
            UserSummary technician,
            Instant createdAt,
            Instant updatedAt) {

        static TicketResponse from(Ticket ticket) {
            return new TicketResponse(
                    ticket.getId(), ticket.getTitle(), ticket.getDescription(), ticket.getStatus(),
                    ticket.getPriority(), ticket.getCategory(), UserSummary.from(ticket.getRequester()),
                    UserSummary.from(ticket.getTechnician()), ticket.getCreatedAt(), ticket.getUpdatedAt());
        }
    }

    public record UserSummary(Long id, String fullName) {
        static UserSummary from(User user) {
            return user == null ? null : new UserSummary(user.getId(), user.getFullName());
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        static <T> PageResponse<T> from(Page<T> result) {
            return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(),
                    result.getTotalElements(), result.getTotalPages());
        }
    }
}
