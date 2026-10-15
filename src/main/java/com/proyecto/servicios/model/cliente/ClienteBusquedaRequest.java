package com.proyecto.servicios.model.cliente;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Criterios de búsqueda de clientes. Todos son opcionales y se combinan con AND.
 */
public record ClienteBusquedaRequest(
        UUID id,
        @Size(max = 18, message = "La CURP no puede exceder 18 caracteres.")
        String curp,
        @Size(max = 13, message = "El RFC no puede exceder 13 caracteres.")
        String rfc,
        @Email(message = "El correo electrónico no tiene un formato válido.")
        @Size(max = 254, message = "El correo no puede exceder 254 caracteres.")
        String correo,
        @Pattern(regexp = "^\\d{1,20}$", message = "El número de cuenta debe contener solo dígitos (máximo 20).")
        String numeroCuenta,
        Boolean activo,
        LocalDate fechaRegistroDesde,
        LocalDate fechaRegistroHasta,
        @Min(value = 0, message = "La página debe ser mayor o igual a 0.")
        Integer pagina,
        @Min(value = 1, message = "El tamaño mínimo es 1.")
        @Max(value = 100, message = "El tamaño máximo es 100.")
        Integer tamano
) {
    public ClienteBusquedaRequest {
        pagina = pagina == null ? 0 : pagina;
        tamano = tamano == null ? 20 : tamano;
    }
}

