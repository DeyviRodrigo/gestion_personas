package com.example.crudpersona.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.Instant;

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PersonaDto {
    private Long id;
    private Long version;
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 60, message = "El nombre no puede superar 60 caracteres")
    private String nombre;
    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 60, message = "El apellido no puede superar 60 caracteres")
    private String apellido;
    @NotBlank(message = "El DNI es obligatorio")
    @Pattern(regexp = "\\d{8}", message = "El DNI debe tener exactamente 8 dígitos")
    private String dni;
    @Email(message = "El correo no tiene un formato válido")
    @Size(max = 120, message = "El correo no puede superar 120 caracteres")
    private String email;
    @Pattern(regexp = "|\\d{9}", message = "El teléfono debe tener 9 dígitos")
    private String telefono;
    private Instant creadoEn;
    private Instant actualizadoEn;
    private String creadoPor;
    private String actualizadoPor;

    public String getNombreCompleto() { return nombre + " " + apellido; }
}
