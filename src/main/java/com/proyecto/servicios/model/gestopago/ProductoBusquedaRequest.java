package com.proyecto.servicios.model.gestopago;

import com.proyecto.servicios.model.cliente.TextoSeguro;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ProductoBusquedaRequest(
        @Min(value = 0, message = "La página no puede ser negativa")
        Integer pagina,

        @Min(value = 1, message = "El tamaño de página debe ser al menos 1")
        @Max(value = 50, message = "El tamaño máximo por página es 50")
        Integer tamano,

        @Size(max = 80, message = "La búsqueda no puede exceder 80 caracteres")
        @TextoSeguro
        String busqueda,

        @Size(max = 50, message = "La categoría no puede exceder 50 caracteres")
        @TextoSeguro
        String categoria,

        @Size(max = 80, message = "El servicio no puede exceder 80 caracteres")
        @TextoSeguro
        String servicio
) {
    public int paginaSegura() {
        return pagina == null ? 0 : pagina;
    }

    public int tamanoSeguro() {
        return tamano == null ? 10 : tamano;
    }
}
