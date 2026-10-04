package br.com.romariosilva.chamados.notification.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.romariosilva.chamados.notification.application.NotificationService;
import br.com.romariosilva.chamados.notification.application.NotificationService.NotificationResponse;
import br.com.romariosilva.chamados.notification.application.NotificationService.UnreadCount;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> list(JwtAuthenticationToken authentication) {
        return notificationService.list(authentication.getToken());
    }

    @GetMapping("/unread-count")
    public UnreadCount unreadCount(JwtAuthenticationToken authentication) {
        return notificationService.unreadCount(authentication.getToken());
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markAsRead(JwtAuthenticationToken authentication, @PathVariable Long id) {
        return notificationService.markAsRead(authentication.getToken(), id);
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(JwtAuthenticationToken authentication) {
        notificationService.markAllAsRead(authentication.getToken());
        return ResponseEntity.noContent().build();
    }
}
