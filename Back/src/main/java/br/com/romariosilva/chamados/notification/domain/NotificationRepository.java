package br.com.romariosilva.chamados.notification.domain;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @EntityGraph(attributePaths = { "ticket", "recipient" })
    List<Notification> findAllByRecipientEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    long countByRecipientEmailIgnoreCaseAndReadAtIsNull(String email);

    boolean existsByTicketIdAndRecipientIdAndType(Long ticketId, Long recipientId, NotificationType type);

    void deleteAllByTicketId(Long ticketId);
}
