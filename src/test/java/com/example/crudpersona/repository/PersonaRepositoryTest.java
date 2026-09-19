package com.example.crudpersona.repository;

import com.example.crudpersona.config.AuditoriaConfig;
import com.example.crudpersona.model.Persona;
import com.example.crudpersona.support.PostgresTestDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AuditoriaConfig.class)
class PersonaRepositoryTest {
    @RegisterExtension static PostgresTestDatabase database = new PostgresTestDatabase();
    @DynamicPropertySource static void configurar(DynamicPropertyRegistry r) { database.configure(r); }
    @Autowired PersonaRepository repository;

    @Test void consultaPorDniYBusquedaPaginadaEnPostgresql() {
        Persona p = repository.saveAndFlush(Persona.builder().nombre("Lucía").apellido("Pérez").dni("01234567").build());
        assertThat(repository.existsByDni("01234567")).isTrue();
        assertThat(repository.findByDni("01234567")).isPresent();
        assertThat(repository.buscar("PÉREZ", PageRequest.of(0, 15)).getContent()).extracting(Persona::getId).containsExactly(p.getId());
        assertThat(p.getCreadoEn()).isNotNull();
        assertThat(p.getCreadoPor()).isEqualTo("pruebas");
    }
}
