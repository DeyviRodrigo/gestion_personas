package com.example.crudpersona.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.Function;
import org.sqlite.SQLiteConnection;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

@Configuration
public class SqliteConfig {
    @Bean(destroyMethod = "close")
    HikariDataSource dataSource(DataSourceProperties properties) {
        String url = properties.determineUrl();
        if (!url.startsWith("jdbc:sqlite:")) {
            throw new IllegalArgumentException("Esta versión utiliza SQLite. Revisa SQLITE_URL o spring.datasource.url.");
        }
        SQLiteDataSource sqlite = new SQLiteDataSource() {
            @Override public SQLiteConnection getConnection(String usuario, String password) throws SQLException {
                SQLiteConnection c = super.getConnection(usuario, password);
                try {
                    // LOWER nativo de SQLite solo convierte ASCII; la búsqueda también debe reconocer Á/Ñ/É.
                    Function.create(c, "lower", new Function() {
                        @Override protected void xFunc() throws SQLException {
                            String valor = value_text(0);
                            if (valor == null) { result(); }
                            else { result(valor.toLowerCase(Locale.ROOT)); }
                        }
                    }, 1, Function.FLAG_DETERMINISTIC);
                    return c;
                } catch (SQLException error) { c.close(); throw error; }
            }
        };
        sqlite.setUrl(url);
        sqlite.setBusyTimeout(10000);
        sqlite.setEnforceForeignKeys(true);
        HikariConfig pool = new HikariConfig();
        pool.setDataSource(sqlite);
        pool.setMaximumPoolSize(1);
        pool.setConnectionTimeout(15000);
        pool.setPoolName("SQLitePersonas");
        return new HikariDataSource(pool);
    }

    @Bean
    FlywayMigrationStrategy migracionesSqlite() {
        return flyway -> {
            boolean anterior;
            boolean personas;
            try (Connection c = flyway.getConfiguration().getDataSource().getConnection()) {
                personas = existeTabla(c, "persona");
                anterior = personas && !existeTabla(c, flyway.getConfiguration().getTable());
                if (anterior) {
                    Set<String> columnas = new HashSet<>();
                    try (var s = c.createStatement(); var r = s.executeQuery("PRAGMA table_info(persona)")) {
                        while (r.next()) { columnas.add(r.getString("name")); }
                    }
                    if (!columnas.equals(Set.of("id", "nombre", "apellido", "dni", "email", "telefono"))) {
                        throw new IllegalStateException("La base SQLite no coincide con la versión anterior. No se ha modificado.");
                    }
                }
            } catch (SQLException e) { throw new IllegalStateException("No se pudo revisar SQLite.", e); }

            if (personas && (anterior || flyway.info().pending().length > 0)) {
                try (Connection c = flyway.getConfiguration().getDataSource().getConnection()) {
                    respaldar(c);
                } catch (Exception e) { throw new IllegalStateException("No se pudo respaldar SQLite; actualización cancelada.", e); }
            }
            if (anterior) { flyway.baseline(); }
            flyway.migrate();
        };
    }

    private boolean existeTabla(Connection c, String nombre) throws SQLException {
        try (var s = c.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            s.setString(1, nombre);
            try (var r = s.executeQuery()) { return r.next(); }
        }
    }

    private void respaldar(Connection c) throws Exception {
        try (var s = c.createStatement(); var r = s.executeQuery("PRAGMA database_list")) {
            while (r.next()) {
                if (!"main".equals(r.getString("name"))) { continue; }
                String archivo = r.getString("file");
                if (archivo == null || archivo.isBlank()) { return; }
                Path carpeta = Path.of(archivo).toAbsolutePath().getParent().resolve("backups");
                Files.createDirectories(carpeta);
                Path destino = carpeta.resolve("personas-antes-actualizacion-" + UUID.randomUUID() + ".db");
                int estado = c.unwrap(SQLiteConnection.class).getDatabase().backup("main", destino.toString(), null);
                if (estado != 0) { throw new SQLException("Respaldo SQLite fallido: " + estado); }
                return;
            }
        }
    }
}
