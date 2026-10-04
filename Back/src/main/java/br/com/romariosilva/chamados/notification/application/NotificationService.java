package br.com.romariosilva.chamados.notification.application;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.romariosilva.chamados.notification.domain.Notification;
import br.com.romariosilva.chamados.notification.domain.NotificationRepository;
import br.com.romariosilva.chamados.notification.domain.NotificationType;
import br.com.romariosilva.chamados.ticket.application.SlaPolicy;
import br.com.romariosilva.chamados.ticket.domain.SlaStatus;
import br.com.romariosilva.chamados.ticket.domain.Ticket;
import br.com.romariosilva.chamados.ticket.domain.TicketRepository;
import br.com.romariosilva.chamados.user.domain.User;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final TicketRepository ticketRepository;
    private final SlaPolicy slaPolicy;

    public NotificationService(NotificationRepository notificationRepository, TicketRepository ticketRepository,
            SlaPolicy slaPolicy) {
        this.notificationRepository = notificationRepository;
        this.ticketRepository = ticketRepository;
        this.slaPolicy = slaPolicy;
    }

    @Scheduled(initialDelayString = "${app.sla-alerts.interval-ms:60000}",
            fixedDelayString = "${app.sla-alerts.interval-ms:60000}")
    @Transactional
    public void generateSlaNotifications() {
        generateSlaNotifications(Instant.now());
    }

    @Transactional
    public void generateSlaNotifications(Instant now) {
        ticketRepository.findAllPendingForSlaAlerts().forEach(ticket -> createAlerts(ticket, now));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(Jwt jwt) {
        return notificationRepository.findAllByRecipientEmailIgnoreCaseOrderByCreatedAtDesc(jwt.getSubject()).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UnreadCount unreadCount(Jwt jwt) {
        return new UnreadCount(notificationRepository
                .countByRecipientEmailIgnoreCaseAndReadAtIsNull(jwt.getSubject()));
    }

    @Transactional
    public NotificationResponse markAsRead(Jwt jwt, Long id) {
        Notification notification = notificationRepository.findById(id)
                .filter(item -> item.getRecipient().getEmail().equalsIgnoreCase(jwt.getSubject()))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Notificação não encontrada"));
        notification.markAsRead(Instant.now());
        return NotificationResponse.from(notification);
    }

    @Transactional
    public void markAllAsRead(Jwt jwt) {
        Instant now = Instant.now();
        notificationRepository.findAllByRecipientEmailIgnoreCaseOrderByCreatedAtDesc(jwt.getSubject())
                .forEach(notification -> notification.markAsRead(now));
    }

    private void createAlerts(Ticket ticket, Instant now) {
        SlaStatus status = slaPolicy.statusOf(ticket, now);
        NotificationType type = switch (status) {
            case EM_RISCO -> NotificationType.SLA_EM_RISCO;
            case VENCIDO -> NotificationType.SLA_VENCIDO;
            default -> null;
        };
        if (type == null) {
            return;
        }

        Set<User> recipients = new LinkedHashSet<>();
        recipients.add(ticket.getRequester());
        if (ticket.getTechnician() != null) {
            recipients.add(ticket.getTechnician());
        }
        recipients.forEach(recipient -> createIfAbsent(ticket, recipient, type));
    }

    private void createIfAbsent(Ticket ticket, User recipient, NotificationType type) {
        if (notificationRepository.existsByTicketIdAndRecipientIdAndType(ticket.getId(), recipient.getId(), type)) {
            return;
        }
        String state = type == NotificationType.SLA_VENCIDO ? "venceu o prazo de SLA" : "está próximo do prazo de SLA";
        notificationRepository.save(new Notification(ticket, recipient, type,
                "Chamado #" + ticket.getId() + " " + state + ": " + ticket.getTitle()));
    }

    public record NotificationResponse(
            Long id,
            Long ticketId,
            NotificationType type,
            String message,
            Instant readAt,
            Instant createdAt) {

        static NotificationResponse from(Notification notification) {
            return new NotificationResponse(notification.getId(), notification.getTicket().getId(),
                    notification.getType(), notification.getMessage(), notification.getReadAt(),
                    notification.getCreatedAt());
        }
    }

    public record UnreadCount(long count) {
    }
}
