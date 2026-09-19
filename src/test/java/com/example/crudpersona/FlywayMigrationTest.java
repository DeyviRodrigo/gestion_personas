package com.example.crudpersona;

import com.example.crudpersona.support.PostgresTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationTest {
    @RegisterExtension static PostgresTestDatabase database = new PostgresTestDatabase();

    @Test void actualizaUnaBaseV1ConDatosYNoRepiteMigraciones() throws Exception {
        var p = database.properties();
        String url = p.get("spring.datasource.url").toString();
        String usuario = p.get("spring.datasource.username").toString();
        String password = p.get("spring.datasource.password").toString();
        String esquema = p.get("spring.flyway.default-schema").toString();
        Flyway.configure().dataSource(url, usuario, password).defaultSchema(esquema)
                .locations("classpath:db/migration").target("1").load().migrate();
        try (var c = DriverManager.getConnection(url, usuario, password); var sql = c.createStatement()) {
            sql.execute("INSERT INTO persona (nombre,apellido,dni) VALUES ('Lucía','Pérez','01234567')");
        }
        Flyway flyway = Flyway.configure().dataSource(url, usuario, password).defaultSchema(esquema)
                .locations("classpath:db/migration").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
        try (var c = DriverManager.getConnection(url, usuario, password); var sql = c.createStatement();
             var fila = sql.executeQuery("SELECT * FROM persona")) {
            assertThat(fila.next()).isTrue();
            assertThat(fila.getString("nombre")).isEqualTo("Lucía");
            assertThat(fila.getString("dni")).isEqualTo("01234567");
            assertThat(fila.getTimestamp("creado_en")).isNotNull();
            assertThat(fila.getString("creado_por")).isEqualTo("migracion");
            assertThat(fila.getLong("version")).isZero();
            assertThat(fila.next()).isFalse();
        }
    }
}
