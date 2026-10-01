package br.com.romariosilva.chamados.user.application;

import java.util.List;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.romariosilva.chamados.user.domain.User;
import br.com.romariosilva.chamados.user.domain.UserRepository;
import br.com.romariosilva.chamados.user.domain.UserRole;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list(Jwt jwt) {
        requireAdmin(jwt);
        return userRepository.findAllByOrderByFullNameAsc().stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listTechnicians(Jwt jwt) {
        requireSupport(jwt);
        return userRepository.findAllByRoleAndActiveTrueOrderByFullNameAsc(UserRole.TECNICO).stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse changeRole(Jwt jwt, Long userId, UserRole role) {
        requireAdmin(jwt);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Usuário não encontrado"));
        user.changeRole(role);
        return UserResponse.from(user);
    }

    private void requireAdmin(Jwt jwt) {
        if (!roles(jwt).contains("ADMIN")) {
            throw new ResponseStatusException(FORBIDDEN, "Acesso restrito a administradores");
        }
    }

    private void requireSupport(Jwt jwt) {
        List<String> roles = roles(jwt);
        if (!roles.contains("ADMIN") && !roles.contains("TECNICO")) {
            throw new ResponseStatusException(FORBIDDEN, "Acesso restrito à equipe de suporte");
        }
    }

    private List<String> roles(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles == null ? List.of() : roles;
    }

    public record UserResponse(Long id, String fullName, String email, UserRole role, boolean active) {
        static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(), user.isActive());
        }
    }
}
