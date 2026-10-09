package com.proyecto.servicios.model.cliente;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class InformacionLaboralRequest {

    @NotNull
    @DecimalMin(value = "0.01", message = "debe ser mayor a cero")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal ingresoMensual;

    @NotBlank
    @Size(max = 100)
    private String ocupacion;

    @NotBlank
    @Size(max = 100)
    private String empresa;
}
