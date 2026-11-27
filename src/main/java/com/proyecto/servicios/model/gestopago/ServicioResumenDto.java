package com.proyecto.servicios.model.gestopago;

public record ServicioResumenDto(
        Integer idServicio,
        String servicio,
        Integer tipoFront,
        String categoria,
        int totalProductos,
        Double precioMinimo,
        Double precioMaximo,
        String logoUrl
) {
    public ServicioResumenDto(
            Integer idServicio,
            String servicio,
            Integer tipoFront,
            String categoria,
            int totalProductos,
            Double precioMinimo,
            Double precioMaximo
    ) {
        this(idServicio, servicio, tipoFront, categoria, totalProductos, precioMinimo, precioMaximo, null);
    }
}
