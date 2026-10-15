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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import com.proyecto.servicios.model.cliente.CuentaBusquedaRequest;

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
    public CuentaResponse obtenerPorNumero(String numeroCuenta, String correoAutenticado, boolean esAdmin) {
        CuentaBancaria cuenta = cuentaRepository.findByNumeroCuenta(numeroCuenta)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "CUENTA_NO_ENCONTRADA", "No se encontró la cuenta bancaria solicitada."));
        validarPropiedadCuenta(cuenta, correoAutenticado, esAdmin);
        return toResponse(cuenta);
    }

    @Override
    public PaginaResponse<CuentaResponse> obtenerCuentas(Boolean activa, int pagina, int tamano) {
        Pageable pageable = crearPagina(pagina, tamano);
        Page<CuentaResponse> cuentas;
        if (activa == null) {
            cuentas = cuentaRepository.findAllByOrderByCreadoEnDesc(pageable).map(this::toResponse);
        } else {
            cuentas = cuentaRepository.findAllByActivaOrderByCreadoEnDesc(activa, pageable).map(this::toResponse);
        }
        return PaginaResponse.desde(cuentas);
    }

    @Override
    public PaginaResponse<CuentaResponse> buscar(CuentaBusquedaRequest criterios, String correoAutenticado,
                                                 boolean esAdmin) {
        Pageable pageable = crearPagina(criterios.pagina(), criterios.tamano());
        Specification<CuentaBancaria> especificacion = (root, query, cb) -> {
            List<Predicate> filtros = new ArrayList<>();
            if (!esAdmin) {
                // Un usuario normal solo puede ver sus propias cuentas (evita IDOR).
                filtros.add(cb.equal(cb.lower(root.join("cliente").join("usuario").get("correo")),
                        correoAutenticado == null ? "" : correoAutenticado.trim().toLowerCase(Locale.ROOT)));
            }
            if (criterios.numeroCuenta() != null && !criterios.numeroCuenta().isBlank()) {
                filtros.add(cb.equal(root.get("numeroCuenta"), criterios.numeroCuenta().trim()));
            }
            if (criterios.clabe() != null && !criterios.clabe().isBlank()) {
                filtros.add(cb.equal(root.get("clabe"), criterios.clabe().trim()));
            }
            if (criterios.clienteId() != null) {
                filtros.add(cb.equal(root.get("cliente").get("idPersona"), criterios.clienteId()));
            }
            if (criterios.activa() != null) {
                filtros.add(cb.equal(root.get("activa"), criterios.activa()));
            }
            return cb.and(filtros.toArray(new Predicate[0]));
        };
        return PaginaResponse.desde(cuentaRepository.findAll(especificacion, pageable).map(this::toResponse));
    }

    @Override
    public PaginaResponse<CuentaResponse> obtenerActivas(int pagina, int tamano) {
        return obtenerCuentas(true, pagina, tamano);
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
    public BigDecimal obtenerSaldo(String numeroCuenta, String correoAutenticado, boolean esAdmin) {
        CuentaBancaria cuenta = cuentaRepository.findByNumeroCuenta(numeroCuenta)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "CUENTA_NO_ENCONTRADA", "No se encontró la cuenta bancaria solicitada."));
        validarPropiedadCuenta(cuenta, correoAutenticado, esAdmin);
        validarOperable(cuenta);
        return cuenta.getSaldo();
    }

    private void validarPropiedadCuenta(CuentaBancaria cuenta, String correoAutenticado, boolean esAdmin) {
        if (esAdmin) {
            return;
        }
        if (cuenta.getCliente() == null || cuenta.getCliente().getUsuario() == null
                || !cuenta.getCliente().getUsuario().getCorreo().equalsIgnoreCase(correoAutenticado)) {
            throw new AccessDeniedException("No tiene permiso para acceder a esta cuenta bancaria.");
        }
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
