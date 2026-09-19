package com.example.crudpersona.service;

import com.example.crudpersona.dto.PersonaDto;
import com.example.crudpersona.exception.NegocioException;
import com.example.crudpersona.mapper.PersonaMapper;
import com.example.crudpersona.model.Persona;
import com.example.crudpersona.repository.PersonaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PersonaServiceTest {
    PersonaRepository repository;
    PersonaService service;

    @BeforeEach void preparar() {
        repository = mock(PersonaRepository.class);
        service = new PersonaService(repository, Mappers.getMapper(PersonaMapper.class));
    }

    @Test void unDniDuplicadoNoLlegaAGuardarse() {
        when(repository.findByDni("12345678")).thenReturn(Optional.of(Persona.builder().id(1L).build()));
        assertThatThrownBy(() -> service.guardar(PersonaDto.builder().nombre("Ana").apellido("Torres").dni("12345678").build()))
                .isInstanceOf(NegocioException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test void mapstructNoPermiteSobrescribirAuditoriaNiVersion() {
        Instant original = Instant.parse("2026-01-01T00:00:00Z");
        Persona entidad = Persona.builder().id(1L).version(2L).creadoEn(original).actualizadoEn(original)
                .creadoPor("original").actualizadoPor("original").build();
        when(repository.findById(1L)).thenReturn(Optional.of(entidad));
        when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        PersonaDto resultado = service.guardar(PersonaDto.builder().id(1L).version(2L)
                .nombre("Ana").apellido("Torres").dni("12345678")
                .creadoPor("falso").actualizadoPor("falso").creadoEn(Instant.EPOCH).build());
        assertThat(resultado.getNombre()).isEqualTo("Ana");
        assertThat(resultado.getCreadoEn()).isEqualTo(original);
        assertThat(resultado.getCreadoPor()).isEqualTo("original");
        assertThat(resultado.getVersion()).isEqualTo(2L);
    }

    @Test void versionObsoletaNoPuedeEditarNiEliminar() {
        when(repository.findById(1L)).thenReturn(Optional.of(Persona.builder().id(1L).version(3L).build()));
        assertThatThrownBy(() -> service.guardar(PersonaDto.builder().id(1L).version(2L).build()))
                .isInstanceOf(NegocioException.class).hasMessageContaining("Otra sesión");
        assertThatThrownBy(() -> service.eliminar(1L, 2L)).isInstanceOf(NegocioException.class);
        verify(repository, never()).saveAndFlush(any());
        verify(repository, never()).delete(any());
    }
}
