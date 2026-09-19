package com.example.crudpersona;

import com.example.crudpersona.dto.PersonaDto;
import com.example.crudpersona.service.PersonaService;
import com.example.crudpersona.support.SqliteTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import java.nio.file.*;
import java.sql.*;
import static org.assertj.core.api.Assertions.*;

class FlywayMigrationTest {
    @Test void actualizaSqliteAnteriorConRespaldoSinPerderDatosYNoRepite() throws Exception {
        var db = new SqliteTestDatabase();
        crearAnterior(db, "INSERT INTO persona VALUES (42,'Lucía','Pérez','01234567',NULL,NULL)");
        try (var app = abrir(db)) {
            var service = app.getBean(PersonaService.class);
            var persona = service.listar(null, 0).getContent().get(0);
            assertThat(persona.getId()).isEqualTo(42L);
            assertThat(persona.getNombre()).isEqualTo("Lucía");
            assertThat(persona.getCreadoEn()).isNotNull();
            assertThat(persona.getCreadoPor()).isEqualTo("migracion-sqlite");
            persona.setNombre("Lucía María");
            service.guardar(persona);
            assertThat(service.guardar(PersonaDto.builder().nombre("Nueva").apellido("Persona").dni("12345678").build()).getId()).isGreaterThan(42);
            assertThat(app.getBean(Flyway.class).info().current().getVersion().getVersion()).isEqualTo("2");
        }
        try (var app = abrir(db)) {
            assertThat(app.getBean(PersonaService.class).contar()).isEqualTo(2);
            assertThat(app.getBean(Flyway.class).info().pending()).isEmpty();
        }
        try (var archivos = Files.list(db.archivo().getParent().resolve("backups"))) {
            var respaldos = archivos.toList();
            assertThat(respaldos).hasSize(1);
            try (var c = DriverManager.getConnection("jdbc:sqlite:" + respaldos.get(0)); var s = c.createStatement();
                 var r = s.executeQuery("SELECT id,nombre FROM persona")) {
                assertThat(r.next()).isTrue();
                assertThat(r.getLong(1)).isEqualTo(42);
                assertThat(r.getString(2)).isEqualTo("Lucía");
                assertThat(r.next()).isFalse();
            }
        }
    }

    @Test void unaBaseNuevaSePreparaSinRespaldosInnecesarios() {
        var db = new SqliteTestDatabase();
        try (var app = abrir(db)) {
            assertThat(app.getBean(PersonaService.class).contar()).isZero();
            assertThat(app.getBean(Flyway.class).info().current().getVersion().getVersion()).isEqualTo("2");
        }
        assertThat(db.archivo()).isRegularFile();
        assertThat(db.archivo().getParent().resolve("backups")).doesNotExist();
    }

    @Test void dniDuplicadoCancelaActualizacionYConservaLaTablaOriginal() throws Exception {
        var db = new SqliteTestDatabase();
        crearAnterior(db, "INSERT INTO persona VALUES (1,'Una','Persona','12345678',NULL,NULL)",
                "INSERT INTO persona VALUES (2,'Otra','Persona','12345678',NULL,NULL)");
        assertThatThrownBy(() -> { try (var ignored = abrir(db)) { } }).isInstanceOf(Exception.class);
        try (var c = conectar(db); var s = c.createStatement(); var r = s.executeQuery("SELECT count(*) FROM persona")) {
            assertThat(r.next()).isTrue(); assertThat(r.getInt(1)).isEqualTo(2);
        }
        try (var c = conectar(db); var s = c.createStatement(); var r = s.executeQuery("PRAGMA table_info(persona)")) {
            int columnas = 0; while (r.next()) { columnas++; }
            assertThat(columnas).isEqualTo(6);
        }
        try (var archivos = Files.list(db.archivo().getParent().resolve("backups"))) {
            assertThat(archivos.toList()).hasSize(1);
        }
    }

    @Test void respaldoIncluyeCambiosConfirmadosEnWal() throws Exception {
        var db = new SqliteTestDatabase();
        crearAnterior(db);
        try (var c = conectar(db); var s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("INSERT INTO persona VALUES (7,'Ana','Torres','12345678',NULL,NULL)");
            try (var app = abrir(db)) { assertThat(app.getBean(PersonaService.class).contar()).isEqualTo(1); }
            try (var archivos = Files.list(db.archivo().getParent().resolve("backups"))) {
                var copia = archivos.findFirst().orElseThrow();
                try (var b = DriverManager.getConnection("jdbc:sqlite:" + copia); var q = b.createStatement();
                     var r = q.executeQuery("SELECT id FROM persona")) {
                    assertThat(r.next()).isTrue(); assertThat(r.getLong(1)).isEqualTo(7);
                }
            }
        }
    }

    private void crearAnterior(SqliteTestDatabase db, String... inserts) throws Exception {
        try (var c = conectar(db); var s = c.createStatement()) {
            s.execute("CREATE TABLE persona (id INTEGER PRIMARY KEY, nombre VARCHAR(60) NOT NULL, apellido VARCHAR(60) NOT NULL, dni VARCHAR(8) NOT NULL, email VARCHAR(120), telefono VARCHAR(9))");
            for (String insert : inserts) { s.execute(insert); }
        }
    }
    private Connection conectar(SqliteTestDatabase db) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + db.archivo());
    }
    private ConfigurableApplicationContext abrir(SqliteTestDatabase db) {
        return new SpringApplicationBuilder(CrudPersonaApplication.class).web(WebApplicationType.NONE)
                .run(db.properties().entrySet().stream().map(e -> "--" + e.getKey() + "=" + e.getValue()).toArray(String[]::new));
    }
}
