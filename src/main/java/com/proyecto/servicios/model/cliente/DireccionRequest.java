package com.proyecto.servicios.model.cliente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DireccionRequest {

    @NotBlank
    @Size(max = 100)
    private String calle;

    @NotBlank
    @Size(max = 20)
    private String numeroExterior;

    @Size(max = 20)
    private String numeroInterior;

    @NotBlank
    @Size(max = 100)
    private String colonia;

    @NotBlank
    @Size(max = 100)
    private String municipio;

    @NotBlank
    @Size(max = 50)
    private String estado;

    @NotBlank
    @Pattern(regexp = "\\d{5}", message = "debe contener exactamente 5 dígitos")
    private String codigoPostal;

    @NotBlank
    @Size(max = 50)
    private String pais = "México";
}
