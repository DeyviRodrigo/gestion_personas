package com.example.crudpersona.mapper;

import com.example.crudpersona.dto.PersonaDto;
import com.example.crudpersona.model.Persona;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface PersonaMapper {
    PersonaDto toDto(Persona persona);

    // La persistencia controla los datos de auditoría y la versión.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "creadoEn", ignore = true)
    @Mapping(target = "actualizadoEn", ignore = true)
    @Mapping(target = "creadoPor", ignore = true)
    @Mapping(target = "actualizadoPor", ignore = true)
    void actualizar(PersonaDto dto, @MappingTarget Persona persona);
}
