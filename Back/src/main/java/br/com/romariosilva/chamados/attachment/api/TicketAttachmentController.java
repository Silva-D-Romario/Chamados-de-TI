package br.com.romariosilva.chamados.attachment.api;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.romariosilva.chamados.attachment.application.TicketAttachmentService;
import br.com.romariosilva.chamados.attachment.application.TicketAttachmentService.AttachmentDownload;
import br.com.romariosilva.chamados.attachment.application.TicketAttachmentService.AttachmentResponse;

@RestController
@RequestMapping("/api/v1/tickets/{ticketId}/attachments")
public class TicketAttachmentController {

    private final TicketAttachmentService attachmentService;

    public TicketAttachmentController(TicketAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentResponse> upload(
            JwtAuthenticationToken authentication,
            @PathVariable Long ticketId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(attachmentService.upload(authentication.getToken(), ticketId, file));
    }

    @GetMapping
    public List<AttachmentResponse> list(
            JwtAuthenticationToken authentication,
            @PathVariable Long ticketId) {
        return attachmentService.list(authentication.getToken(), ticketId);
    }

    @GetMapping("/{attachmentId}")
    public ResponseEntity<Resource> download(
            JwtAuthenticationToken authentication,
            @PathVariable Long ticketId,
            @PathVariable Long attachmentId) {
        AttachmentDownload download = attachmentService.download(authentication.getToken(), ticketId, attachmentId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.originalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.resource());
    }
}
