package com.proyecto.servicios.model.cliente;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/**
 * Criterios de búsqueda de cuentas. Todos son opcionales y se combinan con AND.
 * Un usuario no administrador solo obtiene sus propias cuentas.
 */
public record CuentaBusquedaRequest(
        @Pattern(regexp = "^\\d{1,20}$", message = "El número de cuenta debe contener solo dígitos (máximo 20).")
        String numeroCuenta,
        @Pattern(regexp = "^\\d{1,18}$", message = "La CLABE debe contener solo dígitos (máximo 18).")
        String clabe,
        UUID clienteId,
        Boolean activa,
        @Min(value = 0, message = "La página debe ser mayor o igual a 0.")
        Integer pagina,
        @Min(value = 1, message = "El tamaño mínimo es 1.")
        @Max(value = 100, message = "El tamaño máximo es 100.")
        Integer tamano
) {
    public CuentaBusquedaRequest {
        pagina = pagina == null ? 0 : pagina;
        tamano = tamano == null ? 20 : tamano;
    }
}

