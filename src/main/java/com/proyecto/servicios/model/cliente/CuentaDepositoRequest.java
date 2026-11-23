package com.proyecto.servicios.model.cliente;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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

        String concepto,

        @Size(max = 100, message = "La clave de idempotencia no puede exceder 100 caracteres.")
        String claveIdempotencia
) {
    public CuentaDepositoRequest(String numeroCuenta, BigDecimal monto, String concepto) {
        this(numeroCuenta, monto, concepto, null);
    }

    public CuentaDepositoRequest conClaveIdempotenciaSiFalta(String headerKey) {
        if ((claveIdempotencia == null || claveIdempotencia.isBlank())
                && headerKey != null && !headerKey.isBlank()) {
            String recortada = headerKey.trim();
            if (recortada.length() > 100) {
                recortada = recortada.substring(0, 100);
            }
            return new CuentaDepositoRequest(numeroCuenta, monto, concepto, recortada);
        }
        return this;
    }
}
