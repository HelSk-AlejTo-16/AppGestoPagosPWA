package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.cliente.PaginaResponse;
import com.proyecto.servicios.model.pagos.PagoBusquedaRequest;
import com.proyecto.servicios.model.pagos.PagoServicioRequest;
import com.proyecto.servicios.model.pagos.PagoServicioResponse;
import com.proyecto.servicios.model.pagos.RecargaTelefonicaRequest;
import com.proyecto.servicios.service.PagoServicioService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pagos")
public class PagoServicioController {

    private final PagoServicioService pagoService;

    public PagoServicioController(PagoServicioService pagoService) {
        this.pagoService = pagoService;
    }

    /**
     * Procesa una transacción de pago de servicios públicos o privados (CFE, Telmex, Agua, etc.).
     * Requiere firma de autorización (PIN/contraseña) y referencia del recibo o factura.
     */
    @PostMapping("/servicios")
    public ResponseEntity<GenericResponse> pagarServicio(
            @Valid @RequestBody PagoServicioRequest request,
            Authentication authentication
    ) {
        PagoServicioResponse response = pagoService.procesarPago(request, authentication.getName());
        return ResponseEntity.ok(GenericResponse.ok("Pago de servicio procesado exitosamente.", response));
    }

    /**
     * Procesa una recarga telefónica de tiempo aire o paquete celular (Telcel, Movistar, AT&T, etc.).
     * Requiere firma de autorización (PIN/contraseña) y número celular a 10 dígitos.
     */
    @PostMapping("/recargas")
    public ResponseEntity<GenericResponse> recargarTiempoAire(
            @Valid @RequestBody RecargaTelefonicaRequest request,
            Authentication authentication
    ) {
        PagoServicioResponse response = pagoService.procesarRecarga(request, authentication.getName());
        return ResponseEntity.ok(GenericResponse.ok("Recarga telefónica procesada exitosamente.", response));
    }

    /**
     * Consulta el historial paginado de pagos de servicios y recargas de tiempo aire.
     * Un usuario común solo puede visualizar transacciones pertenecientes a sus cuentas bancarias.
     * Los administradores pueden visualizar la totalidad o filtrar por cuenta.
     */
    @PostMapping({"/servicios/historial", "/historial"})
    public ResponseEntity<GenericResponse> consultarHistorial(
            @Valid @RequestBody PagoBusquedaRequest request,
            Authentication authentication
    ) {
        PaginaResponse<PagoServicioResponse> response = pagoService.consultarHistorial(
                request,
                authentication.getName(),
                esAdmin(authentication)
        );
        return ResponseEntity.ok(GenericResponse.ok("Consulta de historial exitosa.", response));
    }

    private boolean esAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
