package com.proyecto.servicios.exception;

public class AccesoNoAutorizadoException extends RuntimeException {

    private final String codigo;
    private final int status;

    public AccesoNoAutorizadoException(String codigo, String message, int status) {
        super(message);
        this.codigo = codigo;
        this.status = status;
    }

    public String getCodigo() {
        return codigo;
    }

    public int getStatus() {
        return status;
    }
}
