package com.proyecto.servicios.exception;

import com.proyecto.servicios.model.GenericResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CatalogUnavailableException.class)
    public ResponseEntity<GenericResponse> handleCatalogUnavailable(CatalogUnavailableException ex) {
        log.error("Catálogo no disponible: {}", ex.getMessage());
        return build(99, "El servicio de catálogo de productos no está disponible temporalmente. Intente más tarde.",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(GestoPagoAuthException.class)
    public ResponseEntity<GenericResponse> handleAuth(GestoPagoAuthException ex) {
        log.error("Error de autenticación con GestoPago: {}", ex.getMessage());
        return build(91, "Error de autenticación con el proveedor de pagos.", HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(GestoPagoTimeoutException.class)
    public ResponseEntity<GenericResponse> handleTimeout(GestoPagoTimeoutException ex) {
        log.error("Timeout con GestoPago: {}", ex.getMessage());
        return build(92, "El proveedor de pagos no respondió a tiempo.", HttpStatus.GATEWAY_TIMEOUT);
    }

    @ExceptionHandler(GestoPagoCommunicationException.class)
    public ResponseEntity<GenericResponse> handleCommunication(GestoPagoCommunicationException ex) {
        log.error("Error de comunicación con GestoPago: {}", ex.getMessage());
        return build(93, "No fue posible comunicarse con el proveedor de pagos.", HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(GestoPagoResponseException.class)
    public ResponseEntity<GenericResponse> handleResponse(GestoPagoResponseException ex) {
        log.error("Respuesta no exitosa de GestoPago: {}", ex.getMessage());
        return build(94, "El proveedor de pagos devolvió una respuesta no exitosa.", HttpStatus.BAD_GATEWAY);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GenericResponse> handleGenericException(Exception ex) {
        // El detalle solo va al log; al cliente no se le expone información interna.
        log.error("Error no controlado", ex);
        return build(500, "Error interno del servidor. Contacte al administrador.", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<GenericResponse> build(int codigo, String mensaje, HttpStatus status) {
        GenericResponse response = new GenericResponse();
        response.setCodigo(codigo);
        response.setMensaje(mensaje);
        return new ResponseEntity<>(response, status);
    }
}