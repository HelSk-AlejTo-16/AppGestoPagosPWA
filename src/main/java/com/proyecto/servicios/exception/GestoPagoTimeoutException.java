package com.proyecto.servicios.exception;

/**
 * GestoPago no respondió dentro del tiempo límite.
 */
public class GestoPagoTimeoutException extends GestoPagoIntegrationException {

    public GestoPagoTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}