package com.example.crudpersona.service;

import com.example.crudpersona.dto.PersonaDto;
import com.example.crudpersona.exception.NegocioException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.sqlite.SQLiteConnection;

import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SqliteMigrationService {
    private final JdbcTemplate jdbc;
    private final Validator validator;

    public record Resultado(int leidas, int importadas, int existentes, Path respaldo) {}

    @Transactional(rollbackFor = Exception.class)
    public Resultado importar(Path origen, Path directorioRespaldo) throws Exception {
        if (!Files.isRegularFile(origen)) { throw new NegocioException("No existe el archivo SQLite indicado."); }
        Files.createDirectories(directorioRespaldo);
        Path respaldo = directorioRespaldo.resolve("personas-" + UUID.randomUUID() + ".db");
        List<PersonaDto> personas = new ArrayList<>();
        String url = "jdbc:sqlite:" + origen.toAbsolutePath().toUri() + "?mode=ro";
        try (Connection sqlite = DriverManager.getConnection(url)) {
            // La API de backup de SQLite incluye transacciones confirmadas en el WAL.
            int resultado = sqlite.unwrap(SQLiteConnection.class).getDatabase().backup("main", respaldo.toString(), null);
            if (resultado != 0) { throw new SQLException("No se pudo crear el respaldo SQLite: código " + resultado); }
        }
        // Se importa el respaldo consistente, para no leer una base que cambia durante la importación.
        try (Connection sqlite = DriverManager.getConnection("jdbc:sqlite:" + respaldo.toAbsolutePath().toUri() + "?mode=ro");
             Statement statement = sqlite.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id, nombre, apellido, dni, email, telefono FROM persona ORDER BY id")) {
            while (rows.next()) {
                PersonaDto persona = PersonaDto.builder().id(rows.getLong("id"))
                        .nombre(rows.getString("nombre")).apellido(rows.getString("apellido"))
                        .dni(rows.getString("dni")).email(rows.getString("email"))
                        .telefono(rows.getString("telefono")).build();
                var errores = validator.validate(persona);
                if (persona.getId() <= 0 || !errores.isEmpty()) {
                    throw new NegocioException("Registro SQLite " + persona.getId() + " inválido: "
                            + errores.stream().map(v -> v.getMessage()).sorted().collect(Collectors.joining("; ")));
                }
                personas.add(persona);
            }
        }
        // Impide altas/ediciones simultáneas mientras se preservan IDs y se ajusta la secuencia.
        jdbc.execute("LOCK TABLE persona IN EXCLUSIVE MODE");
        int importadas = 0;
        int existentes = 0;
        for (PersonaDto persona : personas) {
            List<PersonaDto> coincidencias = jdbc.query("SELECT id, nombre, apellido, dni, email, telefono FROM persona WHERE id = ? OR dni = ?",
                    (rs, fila) -> PersonaDto.builder().id(rs.getLong("id")).nombre(rs.getString("nombre"))
                            .apellido(rs.getString("apellido")).dni(rs.getString("dni"))
                            .email(rs.getString("email")).telefono(rs.getString("telefono")).build(), persona.getId(), persona.getDni());
            if (!coincidencias.isEmpty()) {
                if (coincidencias.size() != 1 || !iguales(persona, coincidencias.get(0))) {
                    throw new NegocioException("Conflicto en el registro SQLite " + persona.getId()
                            + ". Su ID o DNI ya existe con otros datos. No se importó ningún registro de este archivo.");
                }
                existentes++;
                continue;
            }
            Timestamp ahora = Timestamp.from(Instant.now());
            jdbc.update("INSERT INTO persona (id,nombre,apellido,dni,email,telefono,creado_en,actualizado_en,creado_por,actualizado_por,version) VALUES (?,?,?,?,?,?,?,?,?,?,0)",
                    persona.getId(), persona.getNombre(), persona.getApellido(), persona.getDni(), persona.getEmail(), persona.getTelefono(),
                    ahora, ahora, "migracion-sqlite", "migracion-sqlite");
            importadas++;
        }
        if (importadas > 0) {
            jdbc.queryForObject("SELECT setval(pg_get_serial_sequence('persona','id'), (SELECT max(id) FROM persona), true)", Long.class);
        }
        return new Resultado(personas.size(), importadas, existentes, respaldo);
    }

    private boolean iguales(PersonaDto a, PersonaDto b) {
        return Objects.equals(a.getId(), b.getId()) && Objects.equals(a.getNombre(), b.getNombre())
                && Objects.equals(a.getApellido(), b.getApellido()) && Objects.equals(a.getDni(), b.getDni())
                && Objects.equals(a.getEmail(), b.getEmail()) && Objects.equals(a.getTelefono(), b.getTelefono());
    }
}
