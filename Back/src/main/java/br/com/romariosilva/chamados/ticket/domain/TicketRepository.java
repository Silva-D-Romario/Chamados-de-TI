package br.com.romariosilva.chamados.ticket.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    boolean existsByRequesterEmailIgnoreCaseAndTitle(String requesterEmail, String title);

    @EntityGraph(attributePaths = { "requester", "technician" })
    @Query("SELECT ticket FROM Ticket ticket WHERE ticket.resolvedAt IS NULL")
    List<Ticket> findAllPendingForSlaAlerts();

    @EntityGraph(attributePaths = { "requester", "technician" })
    @Query("""
            SELECT ticket FROM Ticket ticket
            WHERE (CAST(:requesterEmail AS string) IS NULL
                   OR LOWER(ticket.requester.email) = LOWER(CAST(:requesterEmail AS string)))
              AND (:status IS NULL OR ticket.status = :status)
              AND (:priority IS NULL OR ticket.priority = :priority)
              AND (CAST(:category AS string) IS NULL
                   OR LOWER(ticket.category) = LOWER(CAST(:category AS string)))
              AND (:technicianId IS NULL OR ticket.technician.id = :technicianId)
              AND (CAST(:search AS string) IS NULL
                   OR LOWER(ticket.title) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(ticket.description) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
            """)
    Page<Ticket> search(
            @Param("requesterEmail") String requesterEmail,
            @Param("status") TicketStatus status,
            @Param("priority") TicketPriority priority,
            @Param("category") String category,
            @Param("technicianId") Long technicianId,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            SELECT COUNT(ticket) AS total,
                   COALESCE(SUM(CASE WHEN ticket.status = br.com.romariosilva.chamados.ticket.domain.TicketStatus.ABERTO THEN 1 ELSE 0 END), 0) AS open,
                   COALESCE(SUM(CASE WHEN ticket.status IN (br.com.romariosilva.chamados.ticket.domain.TicketStatus.EM_TRIAGEM,
                       br.com.romariosilva.chamados.ticket.domain.TicketStatus.EM_ATENDIMENTO,
                       br.com.romariosilva.chamados.ticket.domain.TicketStatus.AGUARDANDO_USUARIO,
                       br.com.romariosilva.chamados.ticket.domain.TicketStatus.REABERTO) THEN 1 ELSE 0 END), 0) AS inProgress,
                   COALESCE(SUM(CASE WHEN ticket.status IN (br.com.romariosilva.chamados.ticket.domain.TicketStatus.RESOLVIDO,
                       br.com.romariosilva.chamados.ticket.domain.TicketStatus.FECHADO) THEN 1 ELSE 0 END), 0) AS resolved,
                   COALESCE(SUM(CASE WHEN ticket.resolvedAt IS NULL AND ticket.dueAt <= :now THEN 1 ELSE 0 END), 0) AS overdue
            FROM Ticket ticket
            WHERE (CAST(:requesterEmail AS string) IS NULL
                   OR LOWER(ticket.requester.email) = LOWER(CAST(:requesterEmail AS string)))
              AND (:status IS NULL OR ticket.status = :status)
              AND (:priority IS NULL OR ticket.priority = :priority)
              AND (CAST(:category AS string) IS NULL
                   OR LOWER(ticket.category) = LOWER(CAST(:category AS string)))
              AND (:technicianId IS NULL OR ticket.technician.id = :technicianId)
              AND (CAST(:search AS string) IS NULL
                   OR LOWER(ticket.title) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                   OR LOWER(ticket.description) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
            """)
    TicketSummaryProjection summarize(
            @Param("requesterEmail") String requesterEmail,
            @Param("status") TicketStatus status,
            @Param("priority") TicketPriority priority,
            @Param("category") String category,
            @Param("technicianId") Long technicianId,
            @Param("search") String search,
            @Param("now") Instant now);

    interface TicketSummaryProjection {
        Long getTotal();
        Long getOpen();
        Long getInProgress();
        Long getResolved();
        Long getOverdue();
    }
}
