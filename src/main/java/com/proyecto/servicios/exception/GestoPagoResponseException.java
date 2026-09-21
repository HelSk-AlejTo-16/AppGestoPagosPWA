package com.proyecto.servicios.exception;

/**
 * GestoPago respondió con un error HTTP o con un contenido no exitoso o inválido.
 */
public class GestoPagoResponseException extends GestoPagoIntegrationException {

    public GestoPagoResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}