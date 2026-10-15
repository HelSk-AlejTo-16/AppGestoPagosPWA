package com.proyecto.servicios.model.cliente;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;
import java.time.Period;

public class ValidarRegistroClienteValidator implements ConstraintValidator<ValidarRegistroCliente, ClienteRegistroRequest> {

    @Override
    public boolean isValid(ClienteRegistroRequest req, ConstraintValidatorContext context) {
        if (req == null) {
            return true;
        }

        boolean valido = true;

        // 1. Validar mayoría de edad (asociado al campo fechaNacimiento)
        if (req.getFechaNacimiento() != null) {
            int edad = Period.between(req.getFechaNacimiento(), LocalDate.now()).getYears();
            if (edad < 18) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("Debe ser mayor de 18 años")
                        .addPropertyNode("fechaNacimiento")
                        .addConstraintViolation();
                valido = false;
            }
        }

        // 2. Validar CURP detallada (asociado al campo curp)
        if (req.getCurp() != null && !req.getCurp().isBlank()
                && req.getFechaNacimiento() != null && req.getSexo() != null) {
            String errorCurp = ValidacionesIdentificadores.detalleErrorCurp(
                    req.getCurp(), req.getFechaNacimiento(), req.getSexo()
            );
            if (errorCurp != null) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(errorCurp)
                        .addPropertyNode("curp")
                        .addConstraintViolation();
                valido = false;
            }
        }

        // 3. Validar RFC detallado (asociado al campo rfc)
        if (req.getRfc() != null && !req.getRfc().isBlank() && req.getFechaNacimiento() != null) {
            String errorRfc = ValidacionesIdentificadores.detalleErrorRfc(
                    req.getRfc(), req.getFechaNacimiento()
            );
            if (errorRfc != null) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(errorRfc)
                        .addPropertyNode("rfc")
                        .addConstraintViolation();
                valido = false;
            }
        }

        return valido;
    }
}

