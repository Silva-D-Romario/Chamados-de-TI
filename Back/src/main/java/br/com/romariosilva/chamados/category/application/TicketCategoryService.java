package br.com.romariosilva.chamados.category.application;

import java.util.List;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.romariosilva.chamados.category.domain.TicketCategory;
import br.com.romariosilva.chamados.category.domain.TicketCategoryRepository;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class TicketCategoryService {

    private final TicketCategoryRepository categoryRepository;

    public TicketCategoryService(TicketCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listActive() {
        return categoryRepository.findAllByActiveTrueOrderByNameAsc().stream().map(CategoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listAll(Jwt jwt) {
        requireAdmin(jwt);
        return categoryRepository.findAllByOrderByNameAsc().stream().map(CategoryResponse::from).toList();
    }

    @Transactional
    public CategoryResponse create(Jwt jwt, String name) {
        requireAdmin(jwt);
        String normalizedName = normalize(name);
        if (categoryRepository.findByNameIgnoreCase(normalizedName).isPresent()) {
            throw new ResponseStatusException(CONFLICT, "Categoria já cadastrada");
        }
        return CategoryResponse.from(categoryRepository.save(new TicketCategory(normalizedName)));
    }

    @Transactional
    public CategoryResponse update(Jwt jwt, Long id, String name, boolean active) {
        requireAdmin(jwt);
        TicketCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Categoria não encontrada"));
        String normalizedName = normalize(name);
        categoryRepository.findByNameIgnoreCase(normalizedName)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> { throw new ResponseStatusException(CONFLICT, "Categoria já cadastrada"); });
        category.update(normalizedName, active);
        return CategoryResponse.from(category);
    }

    private String normalize(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }

    private void requireAdmin(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null || !roles.contains("ADMIN")) {
            throw new ResponseStatusException(FORBIDDEN, "Acesso restrito a administradores");
        }
    }

    public record CategoryResponse(Long id, String name, boolean active) {
        static CategoryResponse from(TicketCategory category) {
            return new CategoryResponse(category.getId(), category.getName(), category.isActive());
        }
    }
}
