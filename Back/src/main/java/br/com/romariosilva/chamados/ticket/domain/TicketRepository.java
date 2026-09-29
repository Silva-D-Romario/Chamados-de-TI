package br.com.romariosilva.chamados.ticket.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @Override
    @EntityGraph(attributePaths = { "requester", "technician" })
    Page<Ticket> findAll(Pageable pageable);

    @EntityGraph(attributePaths = { "requester", "technician" })
    Page<Ticket> findAllByRequesterEmailIgnoreCase(String email, Pageable pageable);
}
