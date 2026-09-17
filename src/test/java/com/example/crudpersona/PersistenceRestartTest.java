package com.example.crudpersona;

import com.example.crudpersona.model.Persona;
import com.example.crudpersona.service.PersonaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceRestartTest {
    @TempDir Path directorio;

    @Test
    void conservaLosDatosAlCerrarYReabrir() {
        Long id;
        try (var contexto = abrir()) {
            id = contexto.getBean(PersonaService.class).guardar(Persona.builder()
                    .nombre("Ana").apellido("Torres").dni("12345678").build()).getId();
        }
        try (var contexto = abrir()) {
            assertThat(contexto.getBean(PersonaService.class).listar(null, 0).getContent())
                    .extracting(Persona::getId).containsExactly(id);
        }
    }

    private ConfigurableApplicationContext abrir() {
        return new SpringApplicationBuilder(CrudPersonaApplication.class).web(WebApplicationType.NONE)
                .run("--spring.datasource.url=jdbc:sqlite:" + directorio.resolve("persistencia.db"),
                        "--spring.jpa.show-sql=false", "--logging.level.org.hibernate.SQL=WARN");
    }
}
