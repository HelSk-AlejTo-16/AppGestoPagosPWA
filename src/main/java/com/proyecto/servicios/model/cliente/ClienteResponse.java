package com.proyecto.servicios.model.cliente;

import com.proyecto.servicios.entity.cliente.EstadoCivil;
import com.proyecto.servicios.entity.cliente.Sexo;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ClienteResponse(
        UUID id,
        OffsetDateTime creadoEn,
        boolean activo,
        String nombre,
        String segundoNombre,
        String apellidoPaterno,
        String apellidoMaterno,
        LocalDate fechaNacimiento,
        Sexo sexo,
        EstadoCivil estadoCivil,
        String curp,
        String rfc,
        String nacionalidad,
        String correo,
        String telefonoMovil,
        String telefonoAlternativo,
        DireccionResponse direccion,
        InformacionLaboralResponse informacionLaboral,
        List<CuentaResponse> cuentas
) {
}
