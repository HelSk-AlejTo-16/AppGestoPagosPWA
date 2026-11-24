package com.proyecto.servicios.model.cliente;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record LoginRequest(
        @NotBlank(message = "El correo electrónico es obligatorio")
        @Size(max = 100, message = "El correo electrónico no puede exceder los 100 caracteres")
        @Email(message = "El correo electrónico no tiene un formato válido")
        @Pattern(
                regexp = "^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,185}\\.[A-Za-z]{2,24}$",
                message = "El correo electrónico contiene caracteres no permitidos"
        )
        @TextoSeguro
        String correo,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(max = 128, message = "La contraseña excede la longitud máxima permitida")
        String password,

        @Size(max = 20, message = "La plataforma no puede exceder 20 caracteres")
        @Pattern(regexp = "(?i)^(MOVIL|WEB|ESCRITORIO)?$", message = "Plataforma no válida")
        String plataforma
) {
    public LoginRequest(String correo, String password) {
        this(correo, password, "MOVIL");
    }

    public String obtenerPlataformaNormalizada() {
        if (plataforma == null || plataforma.isBlank()) {
            return "MOVIL";
        }
        String limpia = plataforma.trim().toUpperCase(Locale.ROOT);
        return switch (limpia) {
            case "WEB", "ESCRITORIO", "MOVIL" -> limpia;
            default -> "MOVIL";
        };
    }
}
