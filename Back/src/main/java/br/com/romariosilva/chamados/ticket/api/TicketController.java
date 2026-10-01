package br.com.romariosilva.chamados.ticket.api;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.romariosilva.chamados.ticket.application.TicketService;
import br.com.romariosilva.chamados.ticket.application.TicketService.PageResponse;
import br.com.romariosilva.chamados.ticket.application.TicketService.TicketData;
import br.com.romariosilva.chamados.ticket.application.TicketService.TicketHistoryResponse;
import br.com.romariosilva.chamados.ticket.application.TicketService.TicketResponse;
import br.com.romariosilva.chamados.ticket.domain.TicketPriority;
import br.com.romariosilva.chamados.ticket.domain.TicketStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody TicketRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ticketService.create(authentication.getToken(), request.toData()));
    }

    @GetMapping
    public PageResponse<TicketResponse> list(
            JwtAuthenticationToken authentication,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ticketService.list(authentication.getToken(), pageable);
    }

    @GetMapping("/{id}")
    public TicketResponse findById(JwtAuthenticationToken authentication, @PathVariable Long id) {
        return ticketService.findById(authentication.getToken(), id);
    }

    @PutMapping("/{id}")
    public TicketResponse update(
            JwtAuthenticationToken authentication,
            @PathVariable Long id,
            @Valid @RequestBody TicketRequest request) {
        return ticketService.update(authentication.getToken(), id, request.toData());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(JwtAuthenticationToken authentication, @PathVariable Long id) {
        ticketService.delete(authentication.getToken(), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/assignment")
    public TicketResponse assign(
            JwtAuthenticationToken authentication,
            @PathVariable Long id,
            @Valid @RequestBody AssignmentRequest request) {
        return ticketService.assign(authentication.getToken(), id, request.technicianId());
    }

    @PatchMapping("/{id}/status")
    public TicketResponse changeStatus(
            JwtAuthenticationToken authentication,
            @PathVariable Long id,
            @Valid @RequestBody StatusRequest request) {
        return ticketService.changeStatus(authentication.getToken(), id, request.status(), request.note());
    }

    @GetMapping("/{id}/history")
    public List<TicketHistoryResponse> history(
            JwtAuthenticationToken authentication,
            @PathVariable Long id) {
        return ticketService.history(authentication.getToken(), id);
    }

    public record TicketRequest(
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 5000) String description,
            @NotNull TicketPriority priority,
            @NotBlank @Size(max = 80) String category) {

        TicketData toData() {
            return new TicketData(title, description, priority, category);
        }
    }

    public record AssignmentRequest(@NotNull Long technicianId) {
    }

    public record StatusRequest(@NotNull TicketStatus status, @Size(max = 1000) String note) {
    }
}
