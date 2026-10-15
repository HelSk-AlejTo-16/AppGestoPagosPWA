package com.proyecto.servicios.model.cliente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordUpdateRequest(
        @NotBlank(message = "La contraseña actual es obligatoria")
        String passwordActual,

        @NotBlank(message = "La nueva contraseña es obligatoria")
        @PasswordSegura
        String passwordNueva
) {
    public boolean passwordNuevaEsCompatibleConBcrypt() {
        return PasswordPolicy.compatibleConBcrypt(passwordNueva);
    }
}
