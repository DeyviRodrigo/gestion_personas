package com.example.crudpersona;

import com.example.crudpersona.dto.PersonaDto;
import com.example.crudpersona.service.PersonaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import com.example.crudpersona.support.SqliteTestDatabase;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;


import static org.assertj.core.api.Assertions.assertThat;

class PersistenceRestartTest {
    @RegisterExtension static SqliteTestDatabase database = new SqliteTestDatabase();

    @Test
    void conservaLosDatosAlCerrarYReabrir() {
        Long id;
        try (var contexto = abrir()) {
            id = contexto.getBean(PersonaService.class).guardar(PersonaDto.builder()
                    .nombre("Ana").apellido("Torres").dni("12345678").build()).getId();
        }
        try (var contexto = abrir()) {
            assertThat(contexto.getBean(PersonaService.class).listar(null, 0).getContent())
                    .extracting(PersonaDto::getId).containsExactly(id);
        }
    }

    private ConfigurableApplicationContext abrir() {
        return new SpringApplicationBuilder(CrudPersonaApplication.class).web(WebApplicationType.NONE)
                .run(database.properties().entrySet().stream().map(e -> "--" + e.getKey() + "=" + e.getValue()).toArray(String[]::new));
    }
}
