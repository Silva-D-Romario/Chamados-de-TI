package br.com.romariosilva.chamados.ticket.domain;

import java.time.Instant;

import br.com.romariosilva.chamados.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketStatus status = TicketStatus.ABERTO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketPriority priority;

    @Column(nullable = false, length = 80)
    private String category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "technician_id")
    private User technician;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Ticket() {
    }

    public Ticket(String title, String description, TicketPriority priority, String category, User requester,
            Instant dueAt) {
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.category = category;
        this.requester = requester;
        this.dueAt = dueAt;
    }

    public void updateDetails(String title, String description, TicketPriority priority, String category,
            Instant dueAt) {
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.category = category;
        this.dueAt = dueAt;
    }

    public void assignTo(User technician) {
        this.technician = technician;
    }

    public void changeStatus(TicketStatus status) {
        this.status = status;
        if (status == TicketStatus.RESOLVIDO || status == TicketStatus.FECHADO) {
            this.resolvedAt = Instant.now();
        } else if (status == TicketStatus.REABERTO) {
            this.resolvedAt = null;
        }
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public TicketStatus getStatus() { return status; }
    public TicketPriority getPriority() { return priority; }
    public String getCategory() { return category; }
    public User getRequester() { return requester; }
    public User getTechnician() { return technician; }
    public Instant getDueAt() { return dueAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
