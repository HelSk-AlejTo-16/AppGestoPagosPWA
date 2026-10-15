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
@ValidarRegistroCliente
public class ClienteRegistroRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 50, message = "El nombre debe tener entre 2 y 50 caracteres")
    @Pattern(regexp = "[\\p{L}](?:[\\p{L} ]{0,48}[\\p{L}])?", message = "El nombre debe contener solo letras y espacios")
    private String nombre;

    @Size(max = 50, message = "El segundo nombre no puede exceder los 50 caracteres")
    @Pattern(regexp = "(?:|[\\p{L}](?:[\\p{L} ]{0,48}[\\p{L}]))", message = "El segundo nombre debe contener solo letras y espacios")
    private String segundoNombre;

    @NotBlank(message = "El apellido paterno es obligatorio")
    @Size(min = 2, max = 50, message = "El apellido paterno debe tener entre 2 y 50 caracteres")
    @Pattern(regexp = "[\\p{L}](?:[\\p{L} ]{0,48}[\\p{L}])?", message = "El apellido paterno debe contener solo letras y espacios")
    private String apellidoPaterno;

    @Size(max = 50, message = "El apellido materno no puede exceder los 50 caracteres")
    @Pattern(regexp = "(?:|[\\p{L}](?:[\\p{L} ]{0,48}[\\p{L}]))", message = "El apellido materno debe contener solo letras y espacios")
    private String apellidoMaterno;

    @NotNull(message = "La fecha de nacimiento es obligatoria")
    @PastOrPresent(message = "La fecha de nacimiento no puede ser una fecha futura")
    private LocalDate fechaNacimiento;

    @NotNull(message = "El sexo es obligatorio")
    private Sexo sexo;

    @NotNull(message = "El estado civil es obligatorio")
    private EstadoCivil estadoCivil;

    @NotBlank(message = "La CURP es obligatoria")
    @Pattern(
            regexp = "(?i)[A-Z][AEIOUX][A-Z]{2}\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])[HM](AS|BC|BS|CC|CL|CM|CS|CH|DF|DG|GT|GR|HG|JC|MC|MN|MS|NT|NL|OC|PL|QT|QR|SP|SL|SR|TC|TS|TL|VZ|YN|ZS|NE)[B-DF-HJ-NP-TV-Z]{3}[A-Z0-9]\\d",
            message = "La CURP no tiene un formato válido")
    private String curp;

    @NotBlank(message = "El RFC es obligatorio")
    @Pattern(regexp = "(?i)(?:[A-ZÑ&]{3}\\d{6}[A-Z0-9]{3}|[A-ZÑ&]{4}\\d{6}[A-Z0-9]{3})",
            message = "El RFC no tiene un formato válido")
    private String rfc;

    @NotBlank(message = "La nacionalidad es obligatoria")
    @Size(max = 50, message = "La nacionalidad no puede exceder los 50 caracteres")
    private String nacionalidad = "Mexicana";

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El correo electrónico no tiene un formato válido")
    @Size(max = 100, message = "El correo electrónico no puede exceder los 100 caracteres")
    private String correo;

    @NotBlank(message = "La contraseña es obligatoria")
    @PasswordSegura
    private String password;

    @NotBlank(message = "El teléfono móvil es obligatorio")
    @Pattern(regexp = "\\d{10}", message = "El teléfono móvil debe contener exactamente 10 dígitos")
    private String telefonoMovil;

    @Pattern(regexp = "(?:|\\d{10})", message = "El teléfono alternativo debe contener exactamente 10 dígitos")
    private String telefonoAlternativo;

    @NotNull(message = "La dirección es obligatoria")
    @Valid
    private DireccionRequest direccion;

    @NotNull(message = "La información laboral es obligatoria")
    @Valid
    private InformacionLaboralRequest informacionLaboral;

    public void setNombre(String nombre) {
        this.nombre = limpiarTexto(nombre);
    }

    public void setSegundoNombre(String segundoNombre) {
        this.segundoNombre = limpiarTexto(segundoNombre);
    }

    public void setApellidoPaterno(String apellidoPaterno) {
        this.apellidoPaterno = limpiarTexto(apellidoPaterno);
    }

    public void setApellidoMaterno(String apellidoMaterno) {
        this.apellidoMaterno = limpiarTexto(apellidoMaterno);
    }

    public void setCurp(String curp) {
        this.curp = curp != null ? curp.trim().toUpperCase() : null;
    }

    public void setRfc(String rfc) {
        this.rfc = rfc != null ? rfc.trim().toUpperCase() : null;
    }

    public void setCorreo(String correo) {
        this.correo = correo != null ? correo.trim().toLowerCase() : null;
    }

    public void setTelefonoMovil(String telefonoMovil) {
        this.telefonoMovil = telefonoMovil != null ? telefonoMovil.trim() : null;
    }

    public void setTelefonoAlternativo(String telefonoAlternativo) {
        this.telefonoAlternativo = telefonoAlternativo != null ? telefonoAlternativo.trim() : null;
    }

    private static String limpiarTexto(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim().replaceAll("\\s+", " ");
        return limpio.isEmpty() ? null : limpio;
    }
}
