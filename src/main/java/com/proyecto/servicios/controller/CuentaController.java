package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.cliente.CuentaBusquedaRequest;
import com.proyecto.servicios.model.cliente.CuentaConsultaRequest;
import com.proyecto.servicios.model.cliente.CuentaSaldoResponse;
import com.proyecto.servicios.service.CuentaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/cuentas")
public class CuentaController {

    private final CuentaService cuentaService;

    public CuentaController(CuentaService cuentaService) {
        this.cuentaService = cuentaService;
    }

    /**
     * Único endpoint de consulta de cuentas con filtros combinables.
     * Un usuario normal solo recibe sus propias cuentas; el administrador, todas.
     */
    @PostMapping("/buscar")
    public ResponseEntity<GenericResponse> buscar(@Valid @RequestBody CuentaBusquedaRequest criterios,
                                                  Authentication authentication) {
        return ResponseEntity.ok(GenericResponse.ok("Consulta exitosa.",
                cuentaService.buscar(criterios, authentication.getName(), esAdmin(authentication))));
    }

    @PostMapping("/saldo")
    public ResponseEntity<GenericResponse> obtenerSaldo(@Valid @RequestBody CuentaConsultaRequest request,
                                                        Authentication authentication) {
        BigDecimal saldo = cuentaService.obtenerSaldo(
                request.numeroCuenta(), authentication.getName(), esAdmin(authentication));
        return ResponseEntity.ok(GenericResponse.ok("Consulta exitosa.",
                new CuentaSaldoResponse(request.numeroCuenta(), saldo)));
    }

    private boolean esAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
