package com.proyecto.servicios.model.cliente;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UsuarioResponse(
        UUID id,
        UUID clienteId,
        String correo,
        String rol,
        boolean activo,
        OffsetDateTime fechaCreacion,
        OffsetDateTime fechaActualizacion
) {
    public UsuarioResponse(UUID id, UUID clienteId, String correo, boolean activo,
                           OffsetDateTime fechaCreacion, OffsetDateTime fechaActualizacion) {
        this(id, clienteId, correo, "ROLE_USER", activo, fechaCreacion, fechaActualizacion);
    }
}
