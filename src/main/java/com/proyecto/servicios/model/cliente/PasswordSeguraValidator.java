package com.proyecto.servicios.model.cliente;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;

public class PasswordSeguraValidator implements ConstraintValidator<PasswordSegura, String> {

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.isBlank()) {
            return true; // Se valida con @NotBlank
        }

        List<String> faltantes = PasswordPolicy.obtenerRequisitosFaltantes(password);
        if (!faltantes.isEmpty()) {
            context.disableDefaultConstraintViolation();
            String mensaje = "La contraseña debe incluir: " + String.join(", ", faltantes) + ".";
            context.buildConstraintViolationWithTemplate(mensaje)
                    .addConstraintViolation();
            return false;
        }

        return true;
    }
}

