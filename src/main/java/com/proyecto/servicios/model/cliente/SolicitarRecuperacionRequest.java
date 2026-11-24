package com.proyecto.servicios.model.cliente;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SolicitarRecuperacionRequest(
        @NotBlank(message = "El correo electrónico es obligatorio")
        @Size(max = 100, message = "El correo electrónico no puede exceder los 100 caracteres")
        @Email(message = "El formato del correo electrónico no es válido")
        @Pattern(
                regexp = "^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,185}\\.[A-Za-z]{2,24}$",
                message = "El correo electrónico contiene caracteres no permitidos"
        )
        @TextoSeguro
        String correo
) {
}
