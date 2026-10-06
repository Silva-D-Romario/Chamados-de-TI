package br.com.romariosilva.chamados.config;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import br.com.romariosilva.chamados.notification.domain.Notification;
import br.com.romariosilva.chamados.notification.domain.NotificationRepository;
import br.com.romariosilva.chamados.notification.domain.NotificationType;
import br.com.romariosilva.chamados.ticket.domain.Ticket;
import br.com.romariosilva.chamados.ticket.domain.TicketPriority;
import br.com.romariosilva.chamados.ticket.domain.TicketRepository;
import br.com.romariosilva.chamados.user.domain.User;
import br.com.romariosilva.chamados.user.domain.UserRepository;
import br.com.romariosilva.chamados.user.domain.UserRole;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class PostgresMigrationIntegrationTests {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("chamados_test")
            .withUsername("chamados")
            .withPassword("chamados");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.defer-datasource-initialization", () -> false);
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("app.sla-alerts.enabled", () -> false);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void flywayBuildsSchemaAndJpaPersistsRelationshipsOnPostgres() {
        Integer migrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success", Integer.class);
        assertThat(migrations).isEqualTo(6);

        User requester = userRepository.save(new User("Usuário PostgreSQL", "postgres@example.com",
                passwordEncoder.encode("senha-segura"), UserRole.SOLICITANTE));
        Ticket ticket = ticketRepository.save(new Ticket("Validar banco real", "Teste de integração",
                TicketPriority.ALTA, "Hardware", requester, Instant.now().plusSeconds(8 * 60 * 60)));
        notificationRepository.save(new Notification(ticket, requester, NotificationType.SLA_EM_RISCO,
                "Alerta persistido no PostgreSQL"));

        assertThat(ticketRepository.findById(ticket.getId())).isPresent();
        assertThat(ticketRepository.search(requester.getEmail(), null, null, null, null, null,
                PageRequest.of(0, 10))).singleElement().extracting(Ticket::getId).isEqualTo(ticket.getId());
        assertThat(ticketRepository.summarize(requester.getEmail(), null, null, null, null, null, Instant.now())
                .getTotal()).isEqualTo(1);
        assertThat(notificationRepository
                .findAllByRecipientEmailIgnoreCaseOrderByCreatedAtDesc(requester.getEmail()))
                .singleElement()
                .satisfies(notification -> {
                    assertThat(notification.getTicket().getId()).isEqualTo(ticket.getId());
                    assertThat(notification.getType()).isEqualTo(NotificationType.SLA_EM_RISCO);
                });
    }
}
