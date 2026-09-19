package com.example.crudpersona.repository;

import com.example.crudpersona.model.Persona;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PersonaRepository extends JpaRepository<Persona, Long> {

    Optional<Persona> findByDni(String dni);

    boolean existsByDni(String dni);

    @Query("""
            SELECT p FROM Persona p
            WHERE LOWER(p.nombre)   LIKE LOWER(CONCAT('%', :texto, '%')) ESCAPE '\\'
               OR LOWER(p.apellido) LIKE LOWER(CONCAT('%', :texto, '%')) ESCAPE '\\'
               OR p.dni             LIKE CONCAT('%', :texto, '%') ESCAPE '\\'
            """)
    Page<Persona> buscar(@Param("texto") String texto, Pageable pageable);
}
