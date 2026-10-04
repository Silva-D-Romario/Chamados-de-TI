package br.com.romariosilva.chamados.attachment.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, Long> {

    @EntityGraph(attributePaths = "uploader")
    List<TicketAttachment> findAllByTicketIdOrderByCreatedAtAsc(Long ticketId);

    Optional<TicketAttachment> findByIdAndTicketId(Long id, Long ticketId);

    void deleteAllByTicketId(Long ticketId);
}
