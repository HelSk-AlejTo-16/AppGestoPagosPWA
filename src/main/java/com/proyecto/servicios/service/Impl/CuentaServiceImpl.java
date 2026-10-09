package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.cliente.CuentaBancaria;
import com.proyecto.servicios.exception.BusinessConflictException;
import com.proyecto.servicios.exception.ErrorValidacionException;
import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.model.cliente.CuentaResponse;
import com.proyecto.servicios.model.cliente.PaginaResponse;
import com.proyecto.servicios.repositorys.cliente.CuentaBancariaRepository;
import com.proyecto.servicios.service.CuentaService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional(readOnly = true)
public class CuentaServiceImpl implements CuentaService {

    private final CuentaBancariaRepository cuentaRepository;

    public CuentaServiceImpl(CuentaBancariaRepository cuentaRepository) {
        this.cuentaRepository = cuentaRepository;
    }

    @Override
    public CuentaResponse obtenerPorNumero(String numeroCuenta) {
        return cuentaRepository.findByNumeroCuenta(numeroCuenta)
                .map(this::toResponse)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "CUENTA_NO_ENCONTRADA", "No se encontró la cuenta bancaria solicitada."));
    }

    @Override
    public PaginaResponse<CuentaResponse> obtenerActivas(int pagina, int tamano) {
        Pageable pageable = crearPagina(pagina, tamano);
        Page<CuentaResponse> cuentas = cuentaRepository.findAllByActivaTrueOrderByCreadoEnDesc(pageable)
                .map(this::toResponse);
        return PaginaResponse.desde(cuentas);
    }

    @Override
    public BigDecimal obtenerSaldo(String numeroCuenta) {
        return cuentaRepository.findByNumeroCuenta(numeroCuenta)
                .map(cuenta -> {
                    validarOperable(cuenta);
                    return cuenta.getSaldo();
                })
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "CUENTA_NO_ENCONTRADA", "No se encontró la cuenta bancaria solicitada."));
    }

    @Override
    public void validarCuentaOperable(String numeroCuenta) {
        CuentaBancaria cuenta = cuentaRepository.findByNumeroCuenta(numeroCuenta)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "CUENTA_NO_ENCONTRADA", "No se encontró la cuenta bancaria solicitada."));
        validarOperable(cuenta);
    }

    private void validarOperable(CuentaBancaria cuenta) {
        if (!cuenta.isActiva() || !cuenta.getCliente().isActivo()) {
            throw new BusinessConflictException(
                    "CUENTA_INACTIVA", "La cuenta está inactiva y no puede realizar operaciones."
            );
        }
    }

    private Pageable crearPagina(int pagina, int tamano) {
        if (pagina < 0 || tamano < 1 || tamano > 100) {
            throw new ErrorValidacionException(
                    "La página debe ser >= 0 y el tamaño debe estar entre 1 y 100."
            );
        }
        return PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "creadoEn"));
    }

    private CuentaResponse toResponse(CuentaBancaria cuenta) {
        return new CuentaResponse(
                cuenta.getIdBancaria(),
                cuenta.getCliente().getIdPersona(),
                cuenta.getNumeroCuenta(),
                cuenta.getClabe(),
                cuenta.getSaldo(),
                cuenta.isActiva(),
                cuenta.getCreadoEn()
        );
    }
}
