package com.proyecto.servicios.exception;

import com.proyecto.servicios.model.GenericResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.security.access.AccessDeniedException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import jakarta.validation.ConstraintViolationException;

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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<GenericResponse> handleValidation(MethodArgumentNotValidException ex) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("La información enviada no es válida.");
        return build(400, mensaje, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BusinessConflictException.class)
    public ResponseEntity<GenericResponse> handleConflict(BusinessConflictException ex) {
        return build(409, ex.getCodigo() + ": " + ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<GenericResponse> handleDataConflict(DataIntegrityViolationException ex) {
        log.warn("Una restricción de persistencia rechazó la solicitud.");
        return build(409, "Los datos entran en conflicto con un registro existente.", HttpStatus.CONFLICT);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<GenericResponse> handleNotFound(RecursoNoEncontradoException ex) {
        return build(404, ex.getCodigo() + ": " + ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(AccesoNoAutorizadoException.class)
    public ResponseEntity<GenericResponse> handleAuthenticationFailure(AccesoNoAutorizadoException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatus());
        return build(ex.getStatus(), ex.getCodigo() + ": " + ex.getMessage(), status);
    }

    @ExceptionHandler(ErrorValidacionException.class)
    public ResponseEntity<GenericResponse> handleBusinessValidation(ErrorValidacionException ex) {
        return build(400, ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<GenericResponse> handleAccessDenied(AccessDeniedException ex) {
        return build(403, "No tiene permiso para acceder a este recurso.", HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class,
            HandlerMethodValidationException.class
    })
    public ResponseEntity<GenericResponse> handleBadRequest(Exception ex) {
        String mensaje = ex instanceof IllegalArgumentException ? ex.getMessage() : "La solicitud está incompleta o mal formada.";
        return build(400, mensaje, HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<GenericResponse> build(int codigo, String mensaje, HttpStatus status) {
        GenericResponse response = new GenericResponse();
        response.setCodigo(codigo);
        response.setMensaje(mensaje);
        return new ResponseEntity<>(response, status);
    }
}