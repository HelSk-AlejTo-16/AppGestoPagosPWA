package com.proyecto.servicios.model.cliente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DireccionRequest {

    @NotBlank(message = "La calle es obligatoria")
    @Size(max = 100, message = "La calle no puede exceder los 100 caracteres")
    private String calle;

    @NotBlank(message = "El número exterior es obligatorio")
    @Size(max = 20, message = "El número exterior no puede exceder los 20 caracteres")
    private String numeroExterior;

    @Size(max = 20, message = "El número interior no puede exceder los 20 caracteres")
    private String numeroInterior;

    @NotBlank(message = "La colonia es obligatoria")
    @Size(max = 100, message = "La colonia no puede exceder los 100 caracteres")
    private String colonia;

    @NotBlank(message = "El municipio o alcaldía es obligatorio")
    @Size(max = 100, message = "El municipio no puede exceder los 100 caracteres")
    private String municipio;

    @NotBlank(message = "El estado es obligatorio")
    @Size(max = 50, message = "El estado no puede exceder los 50 caracteres")
    private String estado;

    @NotBlank(message = "El código postal es obligatorio")
    @Pattern(regexp = "\\d{5}", message = "El código postal debe contener exactamente 5 dígitos")
    private String codigoPostal;

    @NotBlank(message = "El país es obligatorio")
    @Size(max = 50, message = "El país no puede exceder los 50 caracteres")
    private String pais = "México";
}
