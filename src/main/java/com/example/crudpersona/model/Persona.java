package com.example.crudpersona.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.Instant;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "persona",
        uniqueConstraints = @UniqueConstraint(name = "uk_persona_dni", columnNames = "dni"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "id")
public class Persona {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "INTEGER")
    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 60, message = "El nombre no puede superar 60 caracteres")
    @Column(nullable = false, length = 60)
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 60, message = "El apellido no puede superar 60 caracteres")
    @Column(nullable = false, length = 60)
    private String apellido;

    @NotBlank(message = "El DNI es obligatorio")
    @Pattern(regexp = "\\d{8}", message = "El DNI debe tener exactamente 8 digitos")
    @Column(nullable = false, length = 8)
    private String dni;

    @Email(message = "El correo no tiene un formato valido")
    @Size(max = 120, message = "El correo no puede superar 120 caracteres")
    @Column(length = 120)
    private String email;

    @Pattern(regexp = "|\\d{9}", message = "El telefono debe tener 9 digitos")
    @Column(length = 9)
    private String telefono;

    @Version
    private Long version;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant creadoEn;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant actualizadoEn;

    @CreatedBy
    @Column(nullable = false, updatable = false, length = 120)
    private String creadoPor;

    @LastModifiedBy
    @Column(nullable = false, length = 120)
    private String actualizadoPor;

    public String getNombreCompleto() {
        return nombre + " " + apellido;
    }
}
