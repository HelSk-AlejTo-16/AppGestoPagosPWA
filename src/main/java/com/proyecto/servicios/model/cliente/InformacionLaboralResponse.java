package com.proyecto.servicios.model.cliente;

import java.math.BigDecimal;

public record InformacionLaboralResponse(
        BigDecimal ingresoMensual,
        String ocupacion,
        String empresa
) {
}
