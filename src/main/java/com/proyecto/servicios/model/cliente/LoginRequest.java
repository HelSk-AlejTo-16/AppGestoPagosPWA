package com.proyecto.servicios.model.cliente;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "El correo electrónico es obligatorio")
        @Email(message = "El correo electrónico no tiene un formato válido")
        String correo,

        @NotBlank(message = "La contraseña es obligatoria")
        String password,

        String plataforma
) {
    public LoginRequest(String correo, String password) {
        this(correo, password, "MOVIL");
    }

    public String obtenerPlataformaNormalizada() {
        if (plataforma == null || plataforma.isBlank()) {
            return "MOVIL";
        }
        return plataforma.trim().toUpperCase();
    }
}
