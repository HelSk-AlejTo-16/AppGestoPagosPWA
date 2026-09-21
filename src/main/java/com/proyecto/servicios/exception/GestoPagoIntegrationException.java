package com.proyecto.servicios.exception;

/**
 * Clase base de los errores de integración con GestoPago.
 */
public abstract class GestoPagoIntegrationException extends RuntimeException {

    protected GestoPagoIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}