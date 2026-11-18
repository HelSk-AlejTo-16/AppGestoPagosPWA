package com.proyecto.servicios.model.cliente;

import jakarta.validation.constraints.NotBlank;

public record Reenviar2faRequest(
        @NotBlank(message = "El ticket temporal de 2FA es obligatorio.")
        String ticketTemporal
) {
}
