package com.example.crudpersona.service;

import com.example.crudpersona.dto.PersonaDto;
import com.example.crudpersona.exception.NegocioException;
import com.example.crudpersona.mapper.PersonaMapper;
import com.example.crudpersona.model.Persona;
import com.example.crudpersona.repository.PersonaRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.util.List;
import java.util.Objects;

@Service
@Validated
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class PersonaService {
    public static final int TAMANIO_PAGINA = 15;
    private final PersonaRepository repository;
    private final PersonaMapper mapper;

    public Page<PersonaDto> listar(String filtro, int pagina) {
        Pageable pageable = PageRequest.of(Math.max(pagina, 0), TAMANIO_PAGINA, Sort.by("id"));
        return buscar(filtro, pageable).map(mapper::toDto);
    }

    public List<PersonaDto> listarParaExportar(String filtro) {
        return buscar(filtro, Pageable.unpaged(Sort.by("id"))).map(mapper::toDto).getContent();
    }

    private Page<Persona> buscar(String filtro, Pageable pageable) {
        if (filtro == null || filtro.isBlank()) { return repository.findAll(pageable); }
        String literal = filtro.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return repository.buscar(literal, pageable);
    }

    @Transactional
    public PersonaDto guardar(@NotNull @Valid PersonaDto dto) {
        Persona persona = dto.getId() == null ? new Persona() : obtener(dto.getId());
        if (dto.getId() != null) { comprobarVersion(persona, dto.getVersion()); }
        repository.findByDni(dto.getDni()).ifPresent(existente -> {
            if (!Objects.equals(existente.getId(), dto.getId())) {
                throw new NegocioException("Ya existe una persona registrada con ese DNI.");
            }
        });
        mapper.actualizar(dto, persona);
        persona.setNombre(persona.getNombre().strip());
        persona.setApellido(persona.getApellido().strip());
        try {
            Persona guardada = repository.saveAndFlush(persona);
            log.info("Persona guardada: id={}", guardada.getId());
            return mapper.toDto(guardada);
        } catch (DataIntegrityViolationException e) {
            throw new NegocioException("No se pudo guardar. Comprueba que el DNI no esté registrado y que los datos sean válidos.");
        }
    }

    @Transactional
    public void eliminar(Long id, Long version) {
        Persona persona = obtener(id);
        comprobarVersion(persona, version);
        repository.delete(persona);
        repository.flush();
        log.info("Persona eliminada: id={}", id);
    }

    private Persona obtener(Long id) {
        if (id == null) { throw new NegocioException("Selecciona una persona."); }
        return repository.findById(id).orElseThrow(() -> new NegocioException("La persona ya no existe en la base de datos."));
    }

    private void comprobarVersion(Persona persona, Long version) {
        if (!Objects.equals(persona.getVersion(), version)) {
            throw new NegocioException("Otra sesión modificó este registro. Actualiza la tabla antes de continuar.");
        }
    }

    public long contar() { return repository.count(); }
}
