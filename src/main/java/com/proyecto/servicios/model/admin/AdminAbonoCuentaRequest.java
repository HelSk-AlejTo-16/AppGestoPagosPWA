package com.proyecto.servicios.model.admin;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AdminAbonoCuentaRequest(
        @NotBlank(message = "El número de cuenta es obligatorio.")
        @Pattern(regexp = "^\\d{20}$", message = "El número de cuenta debe tener 20 dígitos.")
        String numeroCuenta,

        @NotNull(message = "El monto es obligatorio.")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero.")
        @Digits(integer = 10, fraction = 2, message = "El monto no puede tener más de 2 decimales.")
        BigDecimal monto,

        @Size(max = 100, message = "El concepto no puede exceder 100 caracteres.")
        String concepto
) {
}
