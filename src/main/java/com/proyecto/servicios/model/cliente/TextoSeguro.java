package com.proyecto.servicios.model.cliente;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TextoSeguroValidator.class)
@Documented
public @interface TextoSeguro {

    String message() default "El campo contiene caracteres o secuencias no permitidas por seguridad";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
