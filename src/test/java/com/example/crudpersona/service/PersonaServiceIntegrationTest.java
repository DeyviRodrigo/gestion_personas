package com.example.crudpersona.service;

import com.example.crudpersona.exception.NegocioException;
import com.example.crudpersona.model.Persona;
import com.example.crudpersona.repository.PersonaRepository;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=WARN"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PersonaServiceIntegrationTest {
    @TempDir static Path directorio;
    @Autowired PersonaService service;
    @Autowired PersonaRepository repository;

    @DynamicPropertySource
    static void baseDePrueba(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + directorio.resolve("personas.db"));
    }

    @BeforeEach
    void limpiar() {
        repository.deleteAllInBatch();
    }

    static Persona persona(String dni) {
        return Persona.builder().nombre("Ana").apellido("Torres").dni(dni)
                .email("ana@example.com").telefono("987654321").build();
    }

    @Test
    void creaLeeActualizaYElimina() {
        Persona guardada = service.guardar(persona("12345678"));
        assertThat(guardada.getId()).isPositive();
        Persona leida = service.listar(null, 0).getContent().get(0);
        assertThat(leida.getNombreCompleto()).isEqualTo("Ana Torres");
        leida.setNombre("Andrea");
        assertThat(service.guardar(leida).getId()).isEqualTo(guardada.getId());
        assertThat(repository.findById(guardada.getId()).orElseThrow().getNombre()).isEqualTo("Andrea");
        assertThat(service.contar()).isEqualTo(1);
        service.eliminar(guardada.getId());
        assertThat(service.contar()).isZero();
    }

    @Test
    void rechazaDniDuplicadoAlCrearYActualizar() {
        Persona primera = service.guardar(persona("12345678"));
        assertThatThrownBy(() -> service.guardar(persona("12345678")))
                .isInstanceOf(NegocioException.class).hasMessageContaining("DNI 12345678");
        Persona segunda = service.guardar(persona("87654321"));
        segunda.setDni(primera.getDni());
        assertThatThrownBy(() -> service.guardar(segunda)).isInstanceOf(NegocioException.class);
        assertThat(repository.findById(segunda.getId()).orElseThrow().getDni()).isEqualTo("87654321");
        assertThat(service.contar()).isEqualTo(2);
    }

    @Test
    void sqliteTambienImpideDniDuplicado() {
        service.guardar(persona("12345678"));
        assertThatThrownBy(() -> repository.saveAndFlush(persona("12345678")))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("SQLITE_CONSTRAINT_UNIQUE");
        assertThat(service.contar()).isEqualTo(1);
    }

    @Test
    void permiteCamposOpcionalesVaciosONulos() {
        Persona vacia = persona("12345678");
        vacia.setEmail("");
        vacia.setTelefono("");
        service.guardar(vacia);
        Persona nula = persona("87654321");
        nula.setEmail(null);
        nula.setTelefono(null);
        service.guardar(nula);
        assertThat(service.contar()).isEqualTo(2);
    }

    static Stream<Consumer<Persona>> datosInvalidos() {
        return Stream.of(p -> p.setNombre(" "), p -> p.setNombre("a".repeat(61)),
                p -> p.setApellido(""), p -> p.setApellido("a".repeat(61)),
                p -> p.setDni("12345"), p -> p.setDni("abcdefgh"), p -> p.setDni(null),
                p -> p.setEmail("correo-invalido"), p -> p.setEmail("a".repeat(121) + "@example.com"),
                p -> p.setTelefono("12345678"), p -> p.setTelefono("abcdefghi"));
    }

    @ParameterizedTest
    @MethodSource("datosInvalidos")
    void validaAntesDePersistir(Consumer<Persona> invalidar) {
        Persona persona = persona("12345678");
        invalidar.accept(persona);
        assertThatThrownBy(() -> service.guardar(persona)).isInstanceOf(ConstraintViolationException.class);
        assertThat(service.contar()).isZero();
    }

    @Test
    void buscaPorNombreApellidoYDniSinDistinguirMayusculas() {
        Persona persona = service.guardar(persona("12345678"));
        for (String filtro : new String[]{" ANA ", "tORRes", "3456"}) {
            assertThat(service.listar(filtro, 0).getContent()).extracting(Persona::getId)
                    .containsExactly(persona.getId());
        }
        assertThat(service.listar("inexistente", 0)).isEmpty();
    }

    @Test
    void paginaDeQuinceEnQuinceYCuentaResultadosFiltrados() {
        for (int i = 0; i < 31; i++) {
            Persona persona = persona(String.format("%08d", i));
            persona.setNombre(i < 16 ? "Ana" : "Luis");
            service.guardar(persona);
        }
        assertThat(service.listar("", 0).getContent()).hasSize(15)
                .extracting(Persona::getId).isSorted();
        assertThat(service.listar("", 1).getContent()).hasSize(15);
        assertThat(service.listar("", 2).getContent()).hasSize(1);
        assertThat(service.listar("", 2).getTotalPages()).isEqualTo(3);
        assertThat(service.listar("Ana", 1).getTotalElements()).isEqualTo(16);
        assertThat(service.listar("Ana", 1).getContent()).hasSize(1);
        assertThat(service.listar(null, -1).getNumber()).isZero();
    }

    @Test
    void noRecreaUnaPersonaEliminadaAlIntentarEditar() {
        Persona persona = service.guardar(persona("12345678"));
        service.eliminar(persona.getId());
        assertThatThrownBy(() -> service.guardar(persona)).isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> service.eliminar(persona.getId())).isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> service.eliminar(null)).isInstanceOf(NegocioException.class);
        assertThat(service.contar()).isZero();
    }
}
