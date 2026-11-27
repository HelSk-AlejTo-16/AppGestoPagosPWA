package com.proyecto.servicios.model.admin;

import com.proyecto.servicios.model.cliente.CuentaResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AdminUsuarioItemResponse(
        UUID idCliente,
        UUID idUsuario,
        String nombreCompleto,
        String correo,
        String rol,
        boolean activo,
        boolean bloqueado,
        int intentosFallidos,
        OffsetDateTime fechaBloqueo,
        String plataformaActiva,
        String curp,
        String rfc,
        String telefonoMovil,
        OffsetDateTime creadoEn,
        List<CuentaResponse> cuentas
) {
}
