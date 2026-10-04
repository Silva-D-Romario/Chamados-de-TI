package br.com.romariosilva.chamados.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.sla-alerts.enabled", havingValue = "true", matchIfMissing = true)
public class SlaAlertsSchedulingConfig {
}
