package com.proyecto.servicios.model.cliente;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RestablecerPasswordRequest(
        @NotBlank(message = "El correo electrónico es obligatorio")
        @Email(message = "El formato del correo electrónico no es válido")
        String correo,

        @NotBlank(message = "El código de verificación es obligatorio")
        @Pattern(regexp = "^\\d{6}$", message = "El código debe contener exactamente 6 dígitos")
        String codigoOtp,

        @NotBlank(message = "La nueva contraseña es obligatoria")
        @PasswordSegura
        String passwordNueva
) {
}
