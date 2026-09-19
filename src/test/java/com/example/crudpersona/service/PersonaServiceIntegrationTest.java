package com.example.crudpersona.service;

import com.example.crudpersona.exception.NegocioException;
import com.example.crudpersona.dto.PersonaDto;
import com.example.crudpersona.model.Persona;
import com.example.crudpersona.mapper.PersonaMapper;
import com.example.crudpersona.support.PostgresTestDatabase;
import org.junit.jupiter.api.extension.RegisterExtension;
import com.example.crudpersona.repository.PersonaRepository;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"spring.jpa.show-sql=false", "logging.level.org.hibernate.SQL=WARN"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PersonaServiceIntegrationTest {
    @RegisterExtension static PostgresTestDatabase database = new PostgresTestDatabase();
    @Autowired PersonaMapper mapper;
    @Autowired PersonaService service;
    @Autowired PersonaRepository repository;

    @DynamicPropertySource
    static void baseDePrueba(DynamicPropertyRegistry registry) {
        database.configure(registry);
    }

    @BeforeEach
    void limpiar() {
        repository.deleteAllInBatch();
    }

    static PersonaDto persona(String dni) {
        return PersonaDto.builder().nombre("Ana").apellido("Torres").dni(dni)
                .email("ana@example.com").telefono("987654321").build();
    }

    @Test
    void creaLeeActualizaYElimina() {
        PersonaDto guardada = service.guardar(persona("12345678"));
        assertThat(guardada.getId()).isPositive();
        PersonaDto leida = service.listar(null, 0).getContent().get(0);
        assertThat(leida.getNombreCompleto()).isEqualTo("Ana Torres");
        leida.setNombre("Andrea");
        assertThat(service.guardar(leida).getId()).isEqualTo(guardada.getId());
        assertThat(repository.findById(guardada.getId()).orElseThrow().getNombre()).isEqualTo("Andrea");
        assertThat(service.contar()).isEqualTo(1);
        service.eliminar(guardada.getId(), service.listar(null, 0).getContent().get(0).getVersion());
        assertThat(service.contar()).isZero();
    }

    @Test
    void rechazaDniDuplicadoAlCrearYActualizar() {
        PersonaDto primera = service.guardar(persona("12345678"));
        assertThatThrownBy(() -> service.guardar(persona("12345678")))
                .isInstanceOf(NegocioException.class).hasMessageContaining("DNI");
        PersonaDto segunda = service.guardar(persona("87654321"));
        segunda.setDni(primera.getDni());
        assertThatThrownBy(() -> service.guardar(segunda)).isInstanceOf(NegocioException.class);
        assertThat(repository.findById(segunda.getId()).orElseThrow().getDni()).isEqualTo("87654321");
        assertThat(service.contar()).isEqualTo(2);
    }

    @Test
    void postgresTambienImpideDniDuplicado() {
        service.guardar(persona("12345678"));
        Persona duplicada = new Persona();
        mapper.actualizar(persona("12345678"), duplicada);
        assertThatThrownBy(() -> repository.saveAndFlush(duplicada))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("uk_persona_dni");
        assertThat(service.contar()).isEqualTo(1);
    }

    @Test
    void permiteCamposOpcionalesVaciosONulos() {
        PersonaDto vacia = persona("12345678");
        vacia.setEmail("");
        vacia.setTelefono("");
        service.guardar(vacia);
        PersonaDto nula = persona("87654321");
        nula.setEmail(null);
        nula.setTelefono(null);
        service.guardar(nula);
        assertThat(service.contar()).isEqualTo(2);
    }

    static Stream<Consumer<PersonaDto>> datosInvalidos() {
        return Stream.of(p -> p.setNombre(" "), p -> p.setNombre("a".repeat(61)),
                p -> p.setApellido(""), p -> p.setApellido("a".repeat(61)),
                p -> p.setDni("12345"), p -> p.setDni("abcdefgh"), p -> p.setDni(null),
                p -> p.setEmail("correo-invalido"), p -> p.setEmail("a".repeat(121) + "@example.com"),
                p -> p.setTelefono("12345678"), p -> p.setTelefono("abcdefghi"));
    }

    @ParameterizedTest
    @MethodSource("datosInvalidos")
    void validaAntesDePersistir(Consumer<PersonaDto> invalidar) {
        PersonaDto persona = persona("12345678");
        invalidar.accept(persona);
        assertThatThrownBy(() -> service.guardar(persona)).isInstanceOf(ConstraintViolationException.class);
        assertThat(service.contar()).isZero();
    }

    @Test
    void buscaPorNombreApellidoYDniSinDistinguirMayusculas() {
        PersonaDto persona = service.guardar(persona("12345678"));
        for (String filtro : new String[]{" ANA ", "tORRes", "3456"}) {
            assertThat(service.listar(filtro, 0).getContent()).extracting(PersonaDto::getId)
                    .containsExactly(persona.getId());
        }
        assertThat(service.listar("inexistente", 0)).isEmpty();
    }

    @Test
    void paginaDeQuinceEnQuinceYCuentaResultadosFiltrados() {
        for (int i = 0; i < 31; i++) {
            PersonaDto persona = persona(String.format("%08d", i));
            persona.setNombre(i < 16 ? "Ana" : "Luis");
            service.guardar(persona);
        }
        assertThat(service.listar("", 0).getContent()).hasSize(15)
                .extracting(PersonaDto::getId).isSorted();
        assertThat(service.listar("", 1).getContent()).hasSize(15);
        assertThat(service.listar("", 2).getContent()).hasSize(1);
        assertThat(service.listar("", 2).getTotalPages()).isEqualTo(3);
        assertThat(service.listar("Ana", 1).getTotalElements()).isEqualTo(16);
        assertThat(service.listar("Ana", 1).getContent()).hasSize(1);
        assertThat(service.listar(null, -1).getNumber()).isZero();
    }

    @Test
    void noRecreaUnaPersonaEliminadaAlIntentarEditar() {
        PersonaDto persona = service.guardar(persona("12345678"));
        service.eliminar(persona.getId(), persona.getVersion());
        assertThatThrownBy(() -> service.guardar(persona)).isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> service.eliminar(persona.getId(), persona.getVersion())).isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> service.eliminar(null, null)).isInstanceOf(NegocioException.class);
        assertThat(service.contar()).isZero();
    }

    @Test
    void auditaAltasYEdicionesSinPerderLaFechaOriginal() {
        PersonaDto inicial = service.guardar(persona("12345678"));
        assertThat(inicial.getCreadoEn()).isNotNull();
        assertThat(inicial.getCreadoPor()).isEqualTo("pruebas");
        PersonaDto obsoleta = inicial.toBuilder().build();
        inicial.setNombre("Andrea");
        PersonaDto actualizada = service.guardar(inicial);
        assertThat(actualizada.getCreadoEn()).isEqualTo(inicial.getCreadoEn());
        assertThat(actualizada.getActualizadoEn()).isAfterOrEqualTo(inicial.getActualizadoEn());
        assertThat(actualizada.getVersion()).isEqualTo(inicial.getVersion() + 1);
        assertThatThrownBy(() -> service.guardar(obsoleta)).isInstanceOf(NegocioException.class);
        assertThatThrownBy(() -> service.eliminar(obsoleta.getId(), obsoleta.getVersion())).isInstanceOf(NegocioException.class);
    }

    @Test
    void exportacionIncluyeTodasLasPaginasYBusquedaTrataPorcentajeComoTexto() {
        for (int i = 0; i < 18; i++) { service.guardar(persona(String.format("%08d", i))); }
        assertThat(service.listarParaExportar("Ana")).hasSize(18);
        assertThat(service.listar("%", 0)).isEmpty();
        assertThat(service.listar("_", 0)).isEmpty();
    }
}
