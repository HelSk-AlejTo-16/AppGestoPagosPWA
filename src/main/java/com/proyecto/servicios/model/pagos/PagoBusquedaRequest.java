package com.proyecto.servicios.model.pagos;

import com.proyecto.servicios.entity.pagos.EstadoPagoServicio;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * Criterios de búsqueda para el historial de pagos de servicios.
 * Se reciben exclusivamente por @RequestBody en formato JSON (sin @RequestParam ni @PathVariable).
 */
public record PagoBusquedaRequest(
        @Pattern(regexp = "^\\d{1,20}$", message = "El número de cuenta debe contener solo dígitos (máximo 20).")
        String numeroCuenta,

        EstadoPagoServicio estado,

        String upc,

        String referencia,

        String idServicio,

        String idProducto,

        @Min(value = 0, message = "La página debe ser mayor o igual a 0.")
        Integer pagina,

        @Min(value = 1, message = "El tamaño mínimo es 1.")
        @Max(value = 100, message = "El tamaño máximo es 100.")
        Integer tamano
) {
    public PagoBusquedaRequest {
        pagina = pagina == null ? 0 : pagina;
        tamano = tamano == null ? 20 : tamano;
    }
}

