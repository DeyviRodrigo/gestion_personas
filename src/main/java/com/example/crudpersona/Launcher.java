package com.example.crudpersona;

import javafx.application.Application;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import com.example.crudpersona.service.SqliteMigrationService;
import com.example.crudpersona.service.PersonaService;

public class Launcher {
    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of(System.getProperty("user.home"), ".gestion-personas"));
        var origen = Arrays.stream(args).filter(a -> a.startsWith("--migrate-sqlite="))
                .map(a -> a.substring("--migrate-sqlite=".length())).findFirst();
        if (origen.isPresent() || Arrays.asList(args).contains("--database-check")) {
            try (var context = new SpringApplicationBuilder(CrudPersonaApplication.class)
                    .web(WebApplicationType.NONE).run(args)) {
                if (origen.isPresent()) {
                    Path respaldos = Path.of(System.getProperty("user.home"), ".gestion-personas", "backups");
                    var resultado = context.getBean(SqliteMigrationService.class).importar(Path.of(origen.get()), respaldos);
                    System.out.printf("Migración terminada: leídas=%d, importadas=%d, ya existentes=%d. Respaldo: %s%n",
                            resultado.leidas(), resultado.importadas(), resultado.existentes(), resultado.respaldo());
                }
                System.out.println("PostgreSQL y Flyway disponibles. Personas: " + context.getBean(PersonaService.class).contar());
            }
            return;
        }
        Application.launch(JavaFxApplication.class, args);
    }
}
