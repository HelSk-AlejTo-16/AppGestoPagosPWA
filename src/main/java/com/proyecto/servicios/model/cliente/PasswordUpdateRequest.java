package com.proyecto.servicios.model.cliente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordUpdateRequest(
        @NotBlank String passwordActual,
        @NotBlank
        @Size(min = 8, max = 72, message = "debe contener entre 8 y 72 caracteres")
        @Pattern(regexp = PasswordPolicy.STRONG_PASSWORD_PATTERN,
                message = "debe incluir mayúscula, minúscula, número y carácter especial")
        String passwordNueva
) {
    public boolean passwordNuevaEsCompatibleConBcrypt() {
        return PasswordPolicy.compatibleConBcrypt(passwordNueva);
    }
}
