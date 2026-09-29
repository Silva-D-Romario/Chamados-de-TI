package br.com.romariosilva.chamados.shared.api;

import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/status")
public class StatusController {

    @GetMapping
    public ResponseEntity<ApiStatusResponse> status() {
        return ResponseEntity.ok(new ApiStatusResponse("chamados-api", "UP", Instant.now()));
    }

    public record ApiStatusResponse(String application, String status, Instant timestamp) {
    }
}

