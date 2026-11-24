package com.proyecto.servicios.model.cliente;

import com.proyecto.servicios.util.InputSanitizer;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class InformacionLaboralRequest {

    @NotNull(message = "El ingreso mensual es obligatorio")
    @DecimalMin(value = "0.01", message = "El ingreso mensual debe ser mayor a cero")
    @Digits(integer = 10, fraction = 2, message = "El ingreso mensual debe tener formato numérico válido")
    private BigDecimal ingresoMensual;

    @NotBlank(message = "La ocupación es obligatoria")
    @Size(min = 2, max = 100, message = "La ocupación debe tener entre 2 y 100 caracteres")
    @Pattern(
            regexp = "^[\\p{L}\\p{N} .,#°º/()&-]{2,100}$",
            message = "La ocupación contiene caracteres no permitidos"
    )
    @TextoSeguro
    private String ocupacion;

    @NotBlank(message = "La empresa es obligatoria")
    @Size(min = 2, max = 100, message = "La empresa debe tener entre 2 y 100 caracteres")
    @Pattern(
            regexp = "^[\\p{L}\\p{N} .,#°º/()&-]{2,100}$",
            message = "La empresa contiene caracteres no permitidos"
    )
    @TextoSeguro
    private String empresa;

    public void setOcupacion(String ocupacion) {
        this.ocupacion = InputSanitizer.sanitizarTexto(ocupacion);
    }

    public void setEmpresa(String empresa) {
        this.empresa = InputSanitizer.sanitizarTexto(empresa);
    }
}
