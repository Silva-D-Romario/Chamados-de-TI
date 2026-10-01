package br.com.romariosilva.chamados.user.api;

import java.util.List;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.romariosilva.chamados.user.application.UserService;
import br.com.romariosilva.chamados.user.application.UserService.UserResponse;
import br.com.romariosilva.chamados.user.domain.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponse> list(JwtAuthenticationToken authentication) {
        return userService.list(authentication.getToken());
    }

    @GetMapping("/technicians")
    public List<UserResponse> listTechnicians(JwtAuthenticationToken authentication) {
        return userService.listTechnicians(authentication.getToken());
    }

    @PatchMapping("/{id}/role")
    public UserResponse changeRole(
            JwtAuthenticationToken authentication,
            @PathVariable Long id,
            @Valid @RequestBody ChangeRoleRequest request) {
        return userService.changeRole(authentication.getToken(), id, request.role());
    }

    public record ChangeRoleRequest(@NotNull UserRole role) {
    }
}
