package br.com.romariosilva.chamados.auth.application;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.romariosilva.chamados.user.domain.User;
import br.com.romariosilva.chamados.user.domain.UserRepository;
import br.com.romariosilva.chamados.user.domain.UserRole;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(String fullName, String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(CONFLICT, "E-mail já cadastrado");
        }

        User user = userRepository.save(new User(
                fullName.trim(),
                normalizedEmail,
                passwordEncoder.encode(password),
                UserRole.SOLICITANTE));

        return createResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(String email, String password) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .filter(User::isActive)
                .orElseThrow(this::invalidCredentials);

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw invalidCredentials();
        }

        return createResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me(Jwt jwt) {
        User user = userRepository.findByEmailIgnoreCase(jwt.getSubject())
                .filter(User::isActive)
                .orElseThrow(this::invalidCredentials);
        return UserResponse.from(user);
    }

    private AuthResponse createResponse(User user) {
        JwtService.Token token = jwtService.issue(user);
        return new AuthResponse(token.value(), "Bearer", token.expiresInSeconds(), UserResponse.from(user));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(UNAUTHORIZED, "E-mail ou senha inválidos");
    }

    public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
    }

    public record UserResponse(Long id, String fullName, String email, UserRole role) {
        static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole());
        }
    }
}
