package com.proyecto.servicios.model.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AdminCambiarRolRequest(
        @NotBlank(message = "El rol es obligatorio.")
        @Pattern(regexp = "^(ROLE_USER|ROLE_ADMIN)$", message = "El rol debe ser ROLE_USER o ROLE_ADMIN.")
        String rol
) {
}
