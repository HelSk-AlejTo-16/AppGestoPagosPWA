package com.proyecto.servicios.model.pagos;

import com.proyecto.servicios.entity.pagos.EstadoPagoServicio;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PagoServicioResponse(
        UUID idPago,
        String numeroCuenta,
        String idServicio,
        String idProducto,
        String nombreServicio,
        String nombreProducto,
        String referencia,
        BigDecimal monto,
        String upc,
        EstadoPagoServicio estado,
        String folioAutorizacion,
        String folioTransaccionGp,
        BigDecimal saldoRestante,
        String mensaje,
        OffsetDateTime fecha
) {
}

