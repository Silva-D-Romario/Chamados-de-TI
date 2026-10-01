package br.com.romariosilva.chamados.ticket.domain;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketHistoryRepository extends JpaRepository<TicketHistory, Long> {

    @EntityGraph(attributePaths = "actor")
    List<TicketHistory> findAllByTicketIdOrderByCreatedAtAsc(Long ticketId);

    void deleteAllByTicketId(Long ticketId);
}
