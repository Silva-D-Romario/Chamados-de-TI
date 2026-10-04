package br.com.romariosilva.chamados.attachment.domain;

import java.time.Instant;

import br.com.romariosilva.chamados.ticket.domain.Ticket;
import br.com.romariosilva.chamados.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "ticket_attachments")
public class TicketAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploader_id", nullable = false)
    private User uploader;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "stored_name", nullable = false, unique = true, length = 80)
    private String storedName;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected TicketAttachment() {
    }

    public TicketAttachment(Ticket ticket, User uploader, String originalName, String storedName,
            String contentType, long sizeBytes) {
        this.ticket = ticket;
        this.uploader = uploader;
        this.originalName = originalName;
        this.storedName = storedName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }

    public Long getId() { return id; }
    public Ticket getTicket() { return ticket; }
    public User getUploader() { return uploader; }
    public String getOriginalName() { return originalName; }
    public String getStoredName() { return storedName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
}
