package com.proyecto.servicios.model.cliente;

import com.proyecto.servicios.util.InputSanitizer;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.text.Normalizer;
import java.util.Locale;

@Getter
@Setter
public class DireccionRequest {

    @NotBlank(message = "La calle es obligatoria")
    @Size(min = 2, max = 100, message = "La calle debe tener entre 2 y 100 caracteres")
    @Pattern(
            regexp = "^[\\p{L}\\p{N} .,#°º/()&-]{2,100}$",
            message = "La calle contiene caracteres no permitidos"
    )
    @TextoSeguro
    private String calle;

    @NotBlank(message = "El número exterior es obligatorio")
    @Size(max = 20, message = "El número exterior no puede exceder los 20 caracteres")
    @Pattern(
            regexp = "^[\\p{L}\\p{N} #./-]{1,20}$",
            message = "El número exterior contiene caracteres no permitidos"
    )
    @TextoSeguro
    private String numeroExterior;

    @Size(max = 20, message = "El número interior no puede exceder los 20 caracteres")
    @Pattern(
            regexp = "(?:|^[\\p{L}\\p{N} #./-]{1,20}$)",
            message = "El número interior contiene caracteres no permitidos"
    )
    @TextoSeguro
    private String numeroInterior;

    @NotBlank(message = "La colonia es obligatoria")
    @Size(min = 2, max = 100, message = "La colonia debe tener entre 2 y 100 caracteres")
    @Pattern(
            regexp = "^[\\p{L}\\p{N} .,#°º/()&'’-]{2,100}$",
            message = "La colonia contiene caracteres no permitidos"
    )
    @TextoSeguro
    private String colonia;

    @NotBlank(message = "El municipio o ciudad es obligatorio")
    @Size(min = 2, max = 100, message = "La ciudad o municipio debe tener entre 2 y 100 caracteres")
    @Pattern(
            regexp = "^[\\p{L}\\p{N} .,#°º/()&'’-]{2,100}$",
            message = "La ciudad o municipio contiene caracteres no permitidos"
    )
    @TextoSeguro
    private String municipio;

    @NotBlank(message = "El estado es obligatorio")
    @Size(min = 2, max = 100, message = "El estado debe tener entre 2 y 100 caracteres")
    @Pattern(
            regexp = "^[\\p{L}\\p{N} .,#°º/()&'’-]{2,100}$",
            message = "El estado contiene caracteres no permitidos"
    )
    @TextoSeguro
    private String estado;

    @NotBlank(message = "El código postal es obligatorio")
    @Size(min = 3, max = 12, message = "El código postal debe tener entre 3 y 12 caracteres")
    @Pattern(
            regexp = "^[A-Za-z0-9][A-Za-z0-9 -]{1,10}[A-Za-z0-9]$",
            message = "El código postal contiene caracteres no permitidos"
    )
    @TextoSeguro
    private String codigoPostal;

    @NotBlank(message = "El país es obligatorio")
    @Size(min = 2, max = 100, message = "El país debe tener entre 2 y 100 caracteres")
    @Pattern(
            regexp = "^[\\p{L}\\p{N} .,#()/&'’-]{2,100}$",
            message = "El país contiene caracteres no permitidos"
    )
    @TextoSeguro
    private String pais = "México";

    @AssertTrue(message = "El código postal de México debe contener exactamente 5 dígitos")
    public boolean isCodigoPostalValidoParaPais() {
        if (codigoPostal == null || pais == null) {
            return true;
        }
        String paisNorm = Normalizer.normalize(pais.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT);
        if ("mexico".equals(paisNorm)) {
            return codigoPostal.matches("\\d{5}");
        }
        return true;
    }

    public void setCalle(String calle) {
        this.calle = InputSanitizer.sanitizarTexto(calle);
    }

    public void setNumeroExterior(String numeroExterior) {
        this.numeroExterior = InputSanitizer.sanitizarTexto(numeroExterior);
    }

    public void setNumeroInterior(String numeroInterior) {
        String limpio = InputSanitizer.sanitizarTexto(numeroInterior);
        this.numeroInterior = (limpio == null || limpio.isEmpty()) ? null : limpio;
    }

    public void setColonia(String colonia) {
        this.colonia = InputSanitizer.sanitizarTexto(colonia);
    }

    public void setMunicipio(String municipio) {
        this.municipio = InputSanitizer.sanitizarTexto(municipio);
    }

    public void setEstado(String estado) {
        this.estado = InputSanitizer.sanitizarTexto(estado);
    }

    public void setCodigoPostal(String codigoPostal) {
        this.codigoPostal = InputSanitizer.sanitizarTexto(codigoPostal);
    }

    public void setPais(String pais) {
        this.pais = InputSanitizer.sanitizarTexto(pais);
    }
}
