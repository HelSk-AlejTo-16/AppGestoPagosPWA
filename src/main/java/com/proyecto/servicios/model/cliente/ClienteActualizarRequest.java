package com.proyecto.servicios.model.cliente;

import com.proyecto.servicios.entity.cliente.EstadoCivil;
import com.proyecto.servicios.entity.cliente.Sexo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ClienteActualizarRequest {

    @NotBlank
    @Size(min = 2, max = 50)
    @Pattern(regexp = "[\\p{L}](?:[\\p{L} ]{0,48}[\\p{L}])?", message = "use solo letras y espacios, sin espacios al inicio o al final")
    private String nombre;

    @Size(max = 50)
    @Pattern(regexp = "(?:|[\\p{L}](?:[\\p{L} ]{0,48}[\\p{L}]))", message = "use solo letras y espacios")
    private String segundoNombre;

    @NotBlank
    @Size(min = 2, max = 50)
    @Pattern(regexp = "[\\p{L}](?:[\\p{L} ]{0,48}[\\p{L}])?", message = "use solo letras y espacios, sin espacios al inicio o al final")
    private String apellidoPaterno;

    @NotBlank
    @Size(min = 2, max = 50)
    @Pattern(regexp = "[\\p{L}](?:[\\p{L} ]{0,48}[\\p{L}])?", message = "use solo letras y espacios, sin espacios al inicio o al final")
    private String apellidoMaterno;

    @NotNull
    @PastOrPresent
    private LocalDate fechaNacimiento;

    @NotNull
    private Sexo sexo;

    @NotNull
    private EstadoCivil estadoCivil;

    @NotBlank
    @Size(max = 50)
    private String nacionalidad;

    @NotBlank
    @Email
    @Size(max = 100)
    private String correo;

    @NotBlank
    @Pattern(regexp = "\\d{10}", message = "debe contener exactamente 10 dígitos")
    private String telefonoMovil;

    @Pattern(regexp = "(?:|\\d{10})", message = "debe contener exactamente 10 dígitos")
    private String telefonoAlternativo;

    @NotNull
    @Valid
    private DireccionRequest direccion;

    @NotNull
    @Valid
    private InformacionLaboralRequest informacionLaboral;

    @jakarta.validation.constraints.AssertTrue(message = "Debe ser mayor de 18 años")
    public boolean isMayorDeEdad() {
        if (fechaNacimiento == null) {
            return true; // Se valida con @NotNull
        }
        return java.time.Period.between(fechaNacimiento, java.time.LocalDate.now()).getYears() >= 18;
    }
}
