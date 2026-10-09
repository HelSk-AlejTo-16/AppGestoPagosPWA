package com.proyecto.servicios.exception;



/**
 * No fue posible comunicarse con GestoPago (red, DNS o conexión rechazada).
 */
public class GestoPagoCommunicationException extends GestoPagoIntegrationException {

    public GestoPagoCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}