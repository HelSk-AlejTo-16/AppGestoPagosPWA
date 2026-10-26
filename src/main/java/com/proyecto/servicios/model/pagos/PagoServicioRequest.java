package com.proyecto.servicios.model.pagos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * Solicitud especializada para transacciones de liquidación de facturas y servicios públicos/privados.
 * Requiere el número de referencia, recibo o contrato de la empresa prestadora.
 */
public record PagoServicioRequest(
        @NotBlank(message = "El número de cuenta es obligatorio.")
        @Pattern(regexp = "^\\d{20}$", message = "El número de cuenta debe contener exactamente 20 dígitos numéricos.")
        String numCuenta,

        @NotBlank(message = "La contraseña o PIN de autorización es obligatorio.")
        String pinAutorizacion,

        @NotBlank(message = "Debes seleccionar el servicio a pagar.")
        String idServicio,

        @NotBlank(message = "Debes seleccionar el tipo de servicio o producto.")
        String idProducto,

        @NotBlank(message = "La referencia o número de servicio es obligatoria.")
        String referencia,

        @NotNull(message = "El monto a pagar es obligatorio.")
        @DecimalMin(value = "0.01", message = "El monto a pagar debe ser mayor a cero.")
        @Digits(integer = 10, fraction = 2, message = "El monto no puede tener más de 2 decimales.")
        BigDecimal monto
) {
}
