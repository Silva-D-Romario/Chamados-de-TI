package br.com.romariosilva.chamados.attachment.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import br.com.romariosilva.chamados.attachment.domain.TicketAttachment;
import br.com.romariosilva.chamados.attachment.domain.TicketAttachmentRepository;
import br.com.romariosilva.chamados.ticket.domain.Ticket;
import br.com.romariosilva.chamados.ticket.domain.TicketRepository;
import br.com.romariosilva.chamados.user.domain.User;
import br.com.romariosilva.chamados.user.domain.UserRepository;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class TicketAttachmentService {

    private static final long MAX_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf", "image/png", "image/jpeg", "text/plain");

    private final TicketAttachmentRepository attachmentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final AttachmentStorage storage;

    public TicketAttachmentService(TicketAttachmentRepository attachmentRepository, TicketRepository ticketRepository,
            UserRepository userRepository, AttachmentStorage storage) {
        this.attachmentRepository = attachmentRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.storage = storage;
    }

    @Transactional
    public AttachmentResponse upload(Jwt jwt, Long ticketId, MultipartFile file) {
        Ticket ticket = visibleTicket(jwt, ticketId);
        validate(file);
        User uploader = currentUser(jwt);
        String storedName = storage.store(file);
        TicketAttachment attachment = attachmentRepository.save(new TicketAttachment(ticket, uploader,
                safeOriginalName(file.getOriginalFilename()), storedName, file.getContentType(), file.getSize()));
        return AttachmentResponse.from(attachment);
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> list(Jwt jwt, Long ticketId) {
        visibleTicket(jwt, ticketId);
        return attachmentRepository.findAllByTicketIdOrderByCreatedAtAsc(ticketId).stream()
                .map(AttachmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AttachmentDownload download(Jwt jwt, Long ticketId, Long attachmentId) {
        visibleTicket(jwt, ticketId);
        TicketAttachment attachment = attachmentRepository.findByIdAndTicketId(attachmentId, ticketId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Anexo não encontrado"));
        return new AttachmentDownload(attachment.getOriginalName(), attachment.getContentType(),
                storage.load(attachment.getStoredName()));
    }

    @Transactional
    public void deleteAllFromTicket(Long ticketId) {
        List<TicketAttachment> attachments = attachmentRepository.findAllByTicketIdOrderByCreatedAtAsc(ticketId);
        attachments.forEach(attachment -> storage.delete(attachment.getStoredName()));
        attachmentRepository.deleteAllByTicketId(ticketId);
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "O anexo está vazio");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new ResponseStatusException(BAD_REQUEST, "O anexo deve ter no máximo 5 MB");
        }
        if (file.getContentType() == null || !ALLOWED_TYPES.contains(file.getContentType())) {
            throw new ResponseStatusException(BAD_REQUEST, "Tipo de arquivo não permitido");
        }
    }

    private String safeOriginalName(String originalName) {
        if (originalName == null || originalName.isBlank()) return "anexo";
        String normalized = originalName.replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (fileName.isBlank()) fileName = "anexo";
        return fileName.length() > 255 ? fileName.substring(fileName.length() - 255) : fileName;
    }

    private User currentUser(Jwt jwt) {
        return userRepository.findByEmailIgnoreCase(jwt.getSubject())
                .filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Usuário não encontrado"));
    }

    private Ticket visibleTicket(Jwt jwt, Long ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Chamado não encontrado"));
        if (!hasSupportAccess(jwt) && !ticket.getRequester().getEmail().equalsIgnoreCase(jwt.getSubject())) {
            throw new ResponseStatusException(NOT_FOUND, "Chamado não encontrado");
        }
        return ticket;
    }

    private boolean hasSupportAccess(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && (roles.contains("TECNICO") || roles.contains("ADMIN"));
    }

    public record AttachmentResponse(Long id, String originalName, String contentType, long sizeBytes,
            UploaderResponse uploader, Instant createdAt) {
        static AttachmentResponse from(TicketAttachment attachment) {
            return new AttachmentResponse(attachment.getId(), attachment.getOriginalName(),
                    attachment.getContentType(), attachment.getSizeBytes(),
                    new UploaderResponse(attachment.getUploader().getId(), attachment.getUploader().getFullName()),
                    attachment.getCreatedAt());
        }
    }

    public record UploaderResponse(Long id, String fullName) {
    }

    public record AttachmentDownload(String originalName, String contentType, Resource resource) {
    }
}
