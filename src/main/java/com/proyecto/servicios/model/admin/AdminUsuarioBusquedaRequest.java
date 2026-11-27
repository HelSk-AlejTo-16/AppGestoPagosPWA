package com.proyecto.servicios.model.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AdminUsuarioBusquedaRequest(
        @Size(max = 100, message = "La búsqueda no puede exceder 100 caracteres.")
        String busqueda,

        Boolean activo,

        @Pattern(regexp = "(?:|ROLE_USER|ROLE_ADMIN)", message = "El rol debe ser ROLE_USER o ROLE_ADMIN.")
        String rol,

        Boolean soloBloqueados,

        @Min(value = 0, message = "La página debe ser mayor o igual a 0.")
        Integer pagina,

        @Min(value = 1, message = "El tamaño mínimo es 1.")
        @Max(value = 100, message = "El tamaño máximo es 100.")
        Integer tamano
) {
    public AdminUsuarioBusquedaRequest {
        pagina = pagina == null ? 0 : pagina;
        tamano = tamano == null ? 15 : tamano;
    }
}
