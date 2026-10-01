package br.com.romariosilva.chamados.ticket.application;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.romariosilva.chamados.ticket.domain.Ticket;
import br.com.romariosilva.chamados.ticket.domain.TicketHistory;
import br.com.romariosilva.chamados.ticket.domain.TicketHistoryAction;
import br.com.romariosilva.chamados.ticket.domain.TicketHistoryRepository;
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

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED_TRANSITIONS = transitions();

    private final TicketRepository ticketRepository;
    private final TicketHistoryRepository historyRepository;
    private final UserRepository userRepository;

    public TicketService(TicketRepository ticketRepository, TicketHistoryRepository historyRepository,
            UserRepository userRepository) {
        this.ticketRepository = ticketRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TicketResponse create(Jwt jwt, TicketData data) {
        User requester = currentUser(jwt);
        Ticket ticket = new Ticket(data.title().trim(), data.description().trim(), data.priority(),
                data.category().trim(), requester);
        ticketRepository.save(ticket);
        historyRepository.save(new TicketHistory(ticket, requester, TicketHistoryAction.CRIADO,
                null, TicketStatus.ABERTO, "Chamado aberto"));
        return TicketResponse.from(ticket);
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
        Ticket ticket = ownedOpenTicket(jwt, id);
        historyRepository.deleteAllByTicketId(id);
        ticketRepository.delete(ticket);
    }

    @Transactional
    public TicketResponse assign(Jwt jwt, Long id, Long technicianId) {
        requireAdmin(jwt);
        User actor = currentUser(jwt);
        User technician = userRepository.findById(technicianId)
                .filter(User::isActive)
                .filter(user -> user.getRole() == br.com.romariosilva.chamados.user.domain.UserRole.TECNICO)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Técnico não encontrado"));
        Ticket ticket = visibleTicket(jwt, id);
        TicketStatus previousStatus = ticket.getStatus();
        ticket.assignTo(technician);
        if (previousStatus == TicketStatus.ABERTO) {
            ticket.changeStatus(TicketStatus.EM_TRIAGEM);
        }
        historyRepository.save(new TicketHistory(ticket, actor, TicketHistoryAction.ATRIBUIDO,
                previousStatus, ticket.getStatus(), "Atribuído a " + technician.getFullName()));
        return TicketResponse.from(ticket);
    }

    @Transactional
    public TicketResponse changeStatus(Jwt jwt, Long id, TicketStatus newStatus, String note) {
        requireSupport(jwt);
        User actor = currentUser(jwt);
        Ticket ticket = visibleTicket(jwt, id);
        if (!isAdmin(jwt) && (ticket.getTechnician() == null
                || !ticket.getTechnician().getId().equals(actor.getId()))) {
            throw new ResponseStatusException(FORBIDDEN, "Chamado não atribuído ao técnico autenticado");
        }
        TicketStatus previousStatus = ticket.getStatus();
        if (!ALLOWED_TRANSITIONS.getOrDefault(previousStatus, Set.of()).contains(newStatus)) {
            throw new ResponseStatusException(CONFLICT,
                    "Transição inválida de " + previousStatus + " para " + newStatus);
        }
        ticket.changeStatus(newStatus);
        historyRepository.save(new TicketHistory(ticket, actor, TicketHistoryAction.STATUS_ALTERADO,
                previousStatus, newStatus, normalizeNote(note)));
        return TicketResponse.from(ticket);
    }

    @Transactional(readOnly = true)
    public List<TicketHistoryResponse> history(Jwt jwt, Long id) {
        visibleTicket(jwt, id);
        return historyRepository.findAllByTicketIdOrderByCreatedAtAsc(id).stream()
                .map(TicketHistoryResponse::from)
                .toList();
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

    private void requireAdmin(Jwt jwt) {
        if (!isAdmin(jwt)) {
            throw new ResponseStatusException(FORBIDDEN, "Apenas administradores podem atribuir chamados");
        }
    }

    private void requireSupport(Jwt jwt) {
        if (!hasSupportAccess(jwt)) {
            throw new ResponseStatusException(FORBIDDEN, "Acesso restrito à equipe de suporte");
        }
    }

    private boolean isAdmin(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains("ADMIN");
    }

    private String normalizeNote(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }

    private static Map<TicketStatus, Set<TicketStatus>> transitions() {
        Map<TicketStatus, Set<TicketStatus>> transitions = new EnumMap<>(TicketStatus.class);
        transitions.put(TicketStatus.ABERTO, Set.of(TicketStatus.EM_TRIAGEM));
        transitions.put(TicketStatus.EM_TRIAGEM, Set.of(TicketStatus.EM_ATENDIMENTO));
        transitions.put(TicketStatus.EM_ATENDIMENTO,
                Set.of(TicketStatus.AGUARDANDO_USUARIO, TicketStatus.RESOLVIDO));
        transitions.put(TicketStatus.AGUARDANDO_USUARIO, Set.of(TicketStatus.EM_ATENDIMENTO));
        transitions.put(TicketStatus.RESOLVIDO, Set.of(TicketStatus.FECHADO, TicketStatus.REABERTO));
        transitions.put(TicketStatus.REABERTO, Set.of(TicketStatus.EM_ATENDIMENTO));
        return Map.copyOf(transitions);
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

    public record TicketHistoryResponse(
            Long id,
            TicketHistoryAction action,
            TicketStatus fromStatus,
            TicketStatus toStatus,
            String note,
            UserSummary actor,
            Instant createdAt) {

        static TicketHistoryResponse from(TicketHistory history) {
            return new TicketHistoryResponse(history.getId(), history.getAction(), history.getFromStatus(),
                    history.getToStatus(), history.getNote(), UserSummary.from(history.getActor()),
                    history.getCreatedAt());
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        static <T> PageResponse<T> from(Page<T> result) {
            return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(),
                    result.getTotalElements(), result.getTotalPages());
        }
    }
}
