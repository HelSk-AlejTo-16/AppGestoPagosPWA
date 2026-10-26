package com.proyecto.servicios.model.pagos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * Solicitud especializada para transacciones de tiempo aire y paquetes de telefonía móvil.
 * Requiere el número celular a 10 dígitos (en lugar de referencia genérica).
 */
public record RecargaTelefonicaRequest(
        @NotBlank(message = "El número de cuenta es obligatorio.")
        @Pattern(regexp = "^\\d{20}$", message = "El número de cuenta debe contener exactamente 20 dígitos numéricos.")
        String numCuenta,

        @NotBlank(message = "La contraseña o PIN de autorización es obligatorio.")
        String pinAutorizacion,

        @NotBlank(message = "Debes seleccionar una compañía telefónica.")
        String idServicio,

        @NotBlank(message = "Debes seleccionar un paquete de recarga.")
        String idProducto,

        @NotBlank(message = "El número celular es obligatorio.")
        @Pattern(regexp = "^\\d{10}$", message = "Ingresa un número celular válido de 10 dígitos numéricos.")
        String telefono,

        @NotNull(message = "El monto de la recarga es obligatorio.")
        @DecimalMin(value = "0.01", message = "El monto a recargar debe ser mayor a cero.")
        @Digits(integer = 10, fraction = 2, message = "El monto no puede tener más de 2 decimales.")
        BigDecimal monto
) {
}

