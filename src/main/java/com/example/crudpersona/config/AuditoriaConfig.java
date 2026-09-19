package com.example.crudpersona.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "relojAuditoria")
public class AuditoriaConfig {
    @Bean
    DateTimeProvider relojAuditoria() {
        // PostgreSQL conserva microsegundos: el DTO y la lectura posterior deben coincidir.
        return () -> Optional.of(Instant.now().truncatedTo(ChronoUnit.MICROS));
    }

    @Bean
    AuditorAware<String> auditor(@Value("${app.audit-user}") String usuario) {
        String nombre = usuario.isBlank() ? "local" : usuario.strip();
        return () -> Optional.of(nombre.substring(0, Math.min(120, nombre.length())));
    }
}
