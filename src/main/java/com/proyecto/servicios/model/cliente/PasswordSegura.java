package com.proyecto.servicios.model.cliente;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordSeguraValidator.class)
@Documented
public @interface PasswordSegura {

    String message() default "La contraseña no cumple con las políticas de seguridad";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

