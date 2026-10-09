package com.proyecto.servicios.model.cliente;

import com.proyecto.servicios.entity.cliente.EstadoCivil;
import com.proyecto.servicios.entity.cliente.Sexo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;
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
public class ClienteRegistroRequest {

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
    @Pattern(
            regexp = "(?i)[A-Z][AEIOUX][A-Z]{2}\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])[HM](AS|BC|BS|CC|CL|CM|CS|CH|DF|DG|GT|GR|HG|JC|MC|MN|MS|NT|NL|OC|PL|QT|QR|SP|SL|SR|TC|TS|TL|VZ|YN|ZS|NE)[B-DF-HJ-NP-TV-Z]{3}[A-Z0-9]\\d",
            message = "no tiene un formato CURP válido")
    private String curp;

    @NotBlank
    @Pattern(regexp = "(?i)(?:[A-ZÑ&]{3}\\d{6}[A-Z0-9]{3}|[A-ZÑ&]{4}\\d{6}[A-Z0-9]{3})",
            message = "no tiene un formato RFC válido")
    private String rfc;

    @NotBlank
    @Size(max = 50)
    private String nacionalidad = "Mexicana";

    @NotBlank
    @Email
    @Size(max = 100)
    private String correo;

    @NotBlank
    @Size(min = 8, max = 72, message = "debe contener entre 8 y 72 caracteres")
    @Pattern(regexp = PasswordPolicy.STRONG_PASSWORD_PATTERN,
            message = "debe incluir mayúscula, minúscula, número y carácter especial")
    private String password;

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

    @AssertTrue(message = "La CURP debe coincidir con la fecha de nacimiento y el sexo, e incluir dígito verificador válido")
    public boolean isCurpConsistente() {
        return curp == null || curp.isBlank()
                || ValidacionesIdentificadores.curpConsistente(curp, fechaNacimiento, sexo);
    }

    @AssertTrue(message = "La fecha incluida en el RFC debe coincidir con la fecha de nacimiento")
    public boolean isRfcConsistente() {
        return rfc == null || rfc.isBlank()
                || ValidacionesIdentificadores.rfcConsistente(rfc, fechaNacimiento);
    }

    @AssertTrue(message = "Debe ser mayor de 18 años")
    public boolean isMayorDeEdad() {
        if (fechaNacimiento == null) {
            return true; // Se valida con @NotNull
        }
        return java.time.Period.between(fechaNacimiento, java.time.LocalDate.now()).getYears() >= 18;
    }
}
