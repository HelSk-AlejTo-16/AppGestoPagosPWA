package com.proyecto.servicios.model.admin;

import jakarta.validation.constraints.NotNull;

public record AdminCambiarEstadoRequest(
        @NotNull(message = "El estado activo es obligatorio.")
        Boolean activo
) {
}
