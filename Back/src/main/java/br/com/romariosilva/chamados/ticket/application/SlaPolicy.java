package br.com.romariosilva.chamados.ticket.application;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import br.com.romariosilva.chamados.ticket.domain.SlaStatus;
import br.com.romariosilva.chamados.ticket.domain.Ticket;
import br.com.romariosilva.chamados.ticket.domain.TicketPriority;

@Component
public class SlaPolicy {

    private static final Map<TicketPriority, Duration> DEADLINES = deadlines();

    public Instant deadlineFor(TicketPriority priority, Instant start) {
        return start.plus(DEADLINES.get(priority));
    }

    public SlaStatus statusOf(Ticket ticket, Instant now) {
        if (ticket.getResolvedAt() != null) {
            return SlaStatus.CONCLUIDO;
        }
        if (ticket.getDueAt() == null || !now.isBefore(ticket.getDueAt())) {
            return SlaStatus.VENCIDO;
        }
        Duration total = Duration.between(ticket.getCreatedAt(), ticket.getDueAt());
        Duration remaining = Duration.between(now, ticket.getDueAt());
        return remaining.compareTo(total.dividedBy(4)) <= 0 ? SlaStatus.EM_RISCO : SlaStatus.NO_PRAZO;
    }

    private static Map<TicketPriority, Duration> deadlines() {
        Map<TicketPriority, Duration> deadlines = new EnumMap<>(TicketPriority.class);
        deadlines.put(TicketPriority.CRITICA, Duration.ofHours(4));
        deadlines.put(TicketPriority.ALTA, Duration.ofHours(8));
        deadlines.put(TicketPriority.MEDIA, Duration.ofHours(24));
        deadlines.put(TicketPriority.BAIXA, Duration.ofHours(48));
        return Map.copyOf(deadlines);
    }
}
