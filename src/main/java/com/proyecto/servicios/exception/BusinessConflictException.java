package com.proyecto.servicios.exception;

public class BusinessConflictException extends RuntimeException {

    private final String codigo;

    public BusinessConflictException(String message) {
        this("BUSINESS_CONFLICT", message);
    }

    public BusinessConflictException(String codigo, String message) {
        super(message);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
