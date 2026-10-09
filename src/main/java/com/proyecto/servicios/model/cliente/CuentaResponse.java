package com.proyecto.servicios.model.cliente;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CuentaResponse(
        UUID id,
        UUID clienteId,
        String numeroCuenta,
        String clabe,
        BigDecimal saldo,
        boolean activa,
        OffsetDateTime creadoEn
) {
}
