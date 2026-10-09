package com.proyecto.servicios.exception;



/**
 * Error de autenticación con GestoPago (401, 403 o token inexistente).
 */
public class GestoPagoAuthException extends GestoPagoIntegrationException {

    public GestoPagoAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}