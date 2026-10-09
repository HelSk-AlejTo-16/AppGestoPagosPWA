package com.proyecto.servicios.model.cliente;

public record DireccionResponse(
        String calle,
        String numeroExterior,
        String numeroInterior,
        String colonia,
        String municipio,
        String estado,
        String codigoPostal,
        String pais
) {
}
