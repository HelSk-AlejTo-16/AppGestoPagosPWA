package com.proyecto.servicios.model.gestopago;

public record ServicioResumenDto(
        Integer idServicio,
        String servicio,
        Integer tipoFront,
        String categoria,
        int totalProductos,
        Double precioMinimo,
        Double precioMaximo
) {
}
