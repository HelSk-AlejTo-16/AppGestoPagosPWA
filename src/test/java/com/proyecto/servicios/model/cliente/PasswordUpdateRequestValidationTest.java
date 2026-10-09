package com.proyecto.servicios.model.cliente;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordUpdateRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "Abc1!", "abcdef1!", "ABCDEF1!", "Abcdefgh!", "Abcdefg1", "Abcdefg1 "
    })
    void actualizacionRechazaPasswordSinRequisito(String password) {
        PasswordUpdateRequest request = new PasswordUpdateRequest("Anterior1!", password);

        assertTrue(validator.validate(request).stream()
                .anyMatch(error -> error.getPropertyPath().toString().equals("passwordNueva")));
    }

    @Test
    void actualizacionAceptaPasswordFuerteConLetrasUnicode() {
        PasswordUpdateRequest request = new PasswordUpdateRequest("Anterior1!", "Ábcdef1!");

        assertFalse(validator.validate(request).stream()
                .anyMatch(error -> error.getPropertyPath().toString().equals("passwordNueva")));
    }

    @Test
    void rechazaPasswordConMasDe72BytesParaBcrypt() {
        PasswordUpdateRequest request = new PasswordUpdateRequest("Anterior1!", "é".repeat(37));

        assertFalse(request.passwordNuevaEsCompatibleConBcrypt());
    }
}
