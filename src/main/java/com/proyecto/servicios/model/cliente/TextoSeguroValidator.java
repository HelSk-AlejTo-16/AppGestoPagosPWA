package com.proyecto.servicios.model.cliente;

import com.proyecto.servicios.util.InputSanitizer;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TextoSeguroValidator implements ConstraintValidator<TextoSeguro, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        return !InputSanitizer.contieneCargaMaliciosa(value);
    }
}
