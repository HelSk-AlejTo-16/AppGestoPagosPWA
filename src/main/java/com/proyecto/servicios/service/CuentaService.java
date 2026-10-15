package com.proyecto.servicios.service;

import com.proyecto.servicios.model.cliente.CuentaBusquedaRequest;
import com.proyecto.servicios.model.cliente.CuentaResponse;
import com.proyecto.servicios.model.cliente.PaginaResponse;

import java.math.BigDecimal;

public interface CuentaService {

    CuentaResponse obtenerPorNumero(String numeroCuenta);

    CuentaResponse obtenerPorNumero(String numeroCuenta, String correoAutenticado, boolean esAdmin);

    PaginaResponse<CuentaResponse> obtenerCuentas(Boolean activa, int pagina, int tamano);

    PaginaResponse<CuentaResponse> buscar(CuentaBusquedaRequest criterios, String correoAutenticado, boolean esAdmin);

    PaginaResponse<CuentaResponse> obtenerActivas(int pagina, int tamano);

    BigDecimal obtenerSaldo(String numeroCuenta);

    BigDecimal obtenerSaldo(String numeroCuenta, String correoAutenticado, boolean esAdmin);

    void validarCuentaOperable(String numeroCuenta);
}
