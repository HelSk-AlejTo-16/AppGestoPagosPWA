package com.proyecto.servicios.model.cliente;

import java.math.BigDecimal;

public record CuentaSaldoResponse(
        String numeroCuenta,
        BigDecimal saldo,
        String moneda
) {
    public static final String MONEDA_DEFAULT = "MXN";

    public CuentaSaldoResponse(String numeroCuenta, BigDecimal saldo) {
        this(numeroCuenta, saldo, MONEDA_DEFAULT);
    }
}

