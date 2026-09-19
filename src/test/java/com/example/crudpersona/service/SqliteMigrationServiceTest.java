package com.example.crudpersona.service;

import com.example.crudpersona.dto.PersonaDto;
import com.example.crudpersona.exception.NegocioException;
import com.example.crudpersona.repository.PersonaRepository;
import com.example.crudpersona.support.PostgresTestDatabase;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.nio.file.*;
import java.sql.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SqliteMigrationServiceTest {
    @RegisterExtension static PostgresTestDatabase database = new PostgresTestDatabase();
    @DynamicPropertySource static void configurar(DynamicPropertyRegistry r) { database.configure(r); }
    @TempDir Path directorio;
    @Autowired SqliteMigrationService migracion;
    @Autowired PersonaService service;
    @Autowired PersonaRepository repository;
    @BeforeEach void limpiar() { repository.deleteAllInBatch(); }

    @Test void migraConRespaldoConservaIdsYNoDuplicaAlRepetir() throws Exception {
        Path source = crearSqlite("INSERT INTO persona VALUES (42,'Ana','Torres','01234567',NULL,'987654321')");
        var resultado = migracion.importar(source, directorio.resolve("backups"));
        assertThat(resultado.leidas()).isEqualTo(1);
        assertThat(resultado.importadas()).isEqualTo(1);
        assertThat(resultado.respaldo()).isRegularFile();
        assertThat(service.listar(null, 0).getContent()).extracting(PersonaDto::getId).containsExactly(42L);
        assertThat(service.listar(null, 0).getContent().get(0).getCreadoPor()).isEqualTo("migracion-sqlite");
        var repetida = migracion.importar(source, directorio.resolve("backups"));
        assertThat(repetida.importadas()).isZero();
        assertThat(repetida.existentes()).isEqualTo(1);
        PersonaDto nueva = service.guardar(PersonaDto.builder().nombre("Luis").apellido("Pérez").dni("87654321").build());
        assertThat(nueva.getId()).isGreaterThan(42);
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + source); var s = c.createStatement(); var rs = s.executeQuery("SELECT count(*) FROM persona")) {
            assertThat(rs.next()).isTrue(); assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test void conflictoRevierteTodoElArchivoSinSobrescribir() throws Exception {
        PersonaDto original = service.guardar(PersonaDto.builder().nombre("Original").apellido("Torres").dni("12345678").build());
        Path source = crearSqlite("INSERT INTO persona VALUES (500,'Nueva','Torres','87654321',NULL,NULL)",
                "INSERT INTO persona VALUES (501,'Distinta','Torres','12345678',NULL,NULL)");
        assertThatThrownBy(() -> migracion.importar(source, directorio.resolve("backups")))
                .isInstanceOf(NegocioException.class).hasMessageContaining("Conflicto");
        assertThat(service.listar(null, 0).getContent()).extracting(PersonaDto::getId).containsExactly(original.getId());
    }

    @Test void datosInvalidosNoSeImportan() throws Exception {
        Path source = crearSqlite("INSERT INTO persona VALUES (1,'Ana','Torres','123',NULL,NULL)");
        assertThatThrownBy(() -> migracion.importar(source, directorio.resolve("backups")))
                .isInstanceOf(NegocioException.class).hasMessageContaining("inválido");
        assertThat(service.contar()).isZero();
    }

    @Test void respaldoIncluyeTransaccionesConfirmadasEnWal() throws Exception {
        Path source = crearSqlite();
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + source); var s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("INSERT INTO persona VALUES (7,'Ana','Torres','12345678',NULL,NULL)");
            assertThat(migracion.importar(source, directorio.resolve("backups")).importadas()).isEqualTo(1);
        }
    }

    private Path crearSqlite(String... inserts) throws Exception {
        Path p = directorio.resolve("origen.db");
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + p); var s = c.createStatement()) {
            s.execute("CREATE TABLE persona (id INTEGER PRIMARY KEY, nombre TEXT, apellido TEXT, dni TEXT, email TEXT, telefono TEXT)");
            for (String insert : inserts) { s.execute(insert); }
        }
        return p;
    }
}
