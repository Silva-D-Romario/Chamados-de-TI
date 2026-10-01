package br.com.romariosilva.chamados.ticket.domain;

import java.time.Instant;

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
@Table(name = "ticket_comments")
public class TicketComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(name = "internal_comment", nullable = false)
    private boolean internal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected TicketComment() {
    }

    public TicketComment(Ticket ticket, User author, String content, boolean internal) {
        this.ticket = ticket;
        this.author = author;
        this.content = content;
        this.internal = internal;
    }

    public Long getId() { return id; }
    public User getAuthor() { return author; }
    public String getContent() { return content; }
    public boolean isInternal() { return internal; }
    public Instant getCreatedAt() { return createdAt; }
}
