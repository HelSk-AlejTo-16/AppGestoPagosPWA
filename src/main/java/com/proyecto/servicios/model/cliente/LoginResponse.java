package com.proyecto.servicios.model.cliente;

public record LoginResponse(
        String token,
        String tipo,
        long expiraEnSegundos,
        String modoAcceso
) {
}
