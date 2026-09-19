package com.example.crudpersona;

import javafx.application.Application;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import com.example.crudpersona.service.PersonaService;

public class Launcher {
    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of(System.getProperty("user.home"), ".gestion-personas"));
        if (Arrays.asList(args).contains("--database-check")) {
            try (var context = new SpringApplicationBuilder(CrudPersonaApplication.class)
                    .web(WebApplicationType.NONE).run(args)) {
                System.out.println("SQLite y Flyway disponibles. Personas: " + context.getBean(PersonaService.class).contar());
            }
            return;
        }
        Application.launch(JavaFxApplication.class, args);
    }
}
