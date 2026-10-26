package com.proyecto.servicios.model.cliente;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * DTO para solicitar un depósito / recarga de fondos a una cuenta bancaria.
 */
public record CuentaDepositoRequest(
        @NotBlank(message = "El número de cuenta es obligatorio.")
        @Pattern(regexp = "^\\d{20}$", message = "El número de cuenta debe contener exactamente 20 dígitos numéricos.")
        String numeroCuenta,

        @NotNull(message = "El monto a depositar es obligatorio.")
        @DecimalMin(value = "0.01", message = "El monto a depositar debe ser mayor a cero.")
        @Digits(integer = 10, fraction = 2, message = "El monto no puede tener más de 2 decimales.")
        BigDecimal monto,

        String concepto
) {
}

