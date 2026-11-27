package com.proyecto.servicios.model.admin;

import java.math.BigDecimal;

public record AdminResumenResponse(
        long totalClientes,
        long clientesActivos,
        long clientesInactivos,
        long usuariosBloqueados,
        long totalCuentas,
        BigDecimal saldoTotalSistema,
        long totalPagos,
        long pagosFallidos
) {
}
