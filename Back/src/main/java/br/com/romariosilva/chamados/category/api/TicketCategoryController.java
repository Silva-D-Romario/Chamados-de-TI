package br.com.romariosilva.chamados.category.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.romariosilva.chamados.category.application.TicketCategoryService;
import br.com.romariosilva.chamados.category.application.TicketCategoryService.CategoryResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/categories")
public class TicketCategoryController {

    private final TicketCategoryService categoryService;

    public TicketCategoryController(TicketCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryResponse> listActive() {
        return categoryService.listActive();
    }

    @GetMapping("/admin")
    public List<CategoryResponse> listAll(JwtAuthenticationToken authentication) {
        return categoryService.listAll(authentication.getToken());
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryService.create(authentication.getToken(), request.name()));
    }

    @PatchMapping("/{id}")
    public CategoryResponse update(
            JwtAuthenticationToken authentication,
            @PathVariable Long id,
            @Valid @RequestBody CategoryUpdateRequest request) {
        return categoryService.update(authentication.getToken(), id, request.name(), request.active());
    }

    public record CategoryRequest(@NotBlank @Size(max = 80) String name) {
    }

    public record CategoryUpdateRequest(@NotBlank @Size(max = 80) String name, boolean active) {
    }
}
