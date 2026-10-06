package br.com.romariosilva.chamados.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import br.com.romariosilva.chamados.ticket.domain.TicketRepository;
import br.com.romariosilva.chamados.user.domain.User;
import br.com.romariosilva.chamados.user.domain.UserRepository;
import br.com.romariosilva.chamados.user.domain.UserRole;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.demo.enabled=true",
        "app.demo.password=senha-demo-segura",
        "app.sla-alerts.enabled=false"
})
class DemoDataBootstrapIntegrationTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Test
    void createsIdempotentDemoUsersAndTickets() {
        assertThat(userRepository.findByEmailIgnoreCase(DemoDataBootstrap.REQUESTER_EMAIL))
                .get()
                .extracting(User::getRole)
                .isEqualTo(UserRole.SOLICITANTE);
        assertThat(userRepository.findByEmailIgnoreCase(DemoDataBootstrap.TECHNICIAN_EMAIL))
                .get()
                .extracting(User::getRole)
                .isEqualTo(UserRole.TECNICO);
        assertThat(ticketRepository.count()).isEqualTo(3);
    }
}
