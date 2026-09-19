package com.example.crudpersona.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Map;

/** Cada clase utiliza su propio archivo SQLite, nunca la base del usuario. */
public class SqliteTestDatabase implements org.junit.jupiter.api.extension.Extension {
    private final Path archivo;

    public SqliteTestDatabase() {
        try {
            Path carpeta = Path.of("target", "test-databases").toAbsolutePath();
            Files.createDirectories(carpeta);
            archivo = Files.createTempDirectory(carpeta, "sqlite-").resolve("persona.db");
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    public Path archivo() { return archivo; }

    public Map<String, Object> properties() {
        return Map.of("spring.config.import", "",
                "spring.datasource.url", "jdbc:sqlite:" + archivo,
                "spring.jpa.show-sql", "false", "app.audit-user", "pruebas");
    }

    public void configure(DynamicPropertyRegistry registry) {
        properties().forEach((key, value) -> registry.add(key, () -> value));
    }
}
