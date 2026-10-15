package com.proyecto.servicios.model.cliente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Identifica una cuenta por su número en el cuerpo de la solicitud. */
public record CuentaConsultaRequest(
        @NotBlank(message = "El número de cuenta es obligatorio.")
        @Pattern(regexp = "^\\d{1,20}$", message = "El número de cuenta debe contener solo dígitos (máximo 20).")
        String numeroCuenta
) {
}

