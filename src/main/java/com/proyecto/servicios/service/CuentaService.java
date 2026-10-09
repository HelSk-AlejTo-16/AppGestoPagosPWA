package com.proyecto.servicios.service;

import com.proyecto.servicios.model.cliente.CuentaResponse;
import com.proyecto.servicios.model.cliente.PaginaResponse;

import java.math.BigDecimal;

public interface CuentaService {

    CuentaResponse obtenerPorNumero(String numeroCuenta);

    PaginaResponse<CuentaResponse> obtenerActivas(int pagina, int tamano);

    BigDecimal obtenerSaldo(String numeroCuenta);

    void validarCuentaOperable(String numeroCuenta);
}
