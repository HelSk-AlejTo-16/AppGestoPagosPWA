package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.cliente.ClienteActualizarRequest;
import com.proyecto.servicios.model.cliente.ClienteBusquedaRequest;
import com.proyecto.servicios.model.cliente.ClienteRegistroRequest;
import com.proyecto.servicios.service.ClienteService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sin {@code @PathVariable} ni {@code @RequestParam}: la identidad del usuario sale del token JWT
 * y los criterios de consulta viajan en un DTO dentro del cuerpo de la solicitud.
 */
@RestController
@RequestMapping("/clientes")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @PostMapping
    @Operation(security = {})
    public ResponseEntity<GenericResponse> registrar(@Valid @RequestBody ClienteRegistroRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GenericResponse.ok("Cliente registrado correctamente.", clienteService.registrar(request)));
    }

    @GetMapping("/me")
    public ResponseEntity<GenericResponse> obtenerMiPerfil(Authentication authentication) {
        return ResponseEntity.ok(GenericResponse.ok("Consulta exitosa.",
                clienteService.obtenerMiPerfil(authentication.getName())));
    }

    @PutMapping("/me")
    public ResponseEntity<GenericResponse> actualizarMiPerfil(Authentication authentication,
                                                              @Valid @RequestBody ClienteActualizarRequest request) {
        return ResponseEntity.ok(GenericResponse.ok("Perfil actualizado correctamente.",
                clienteService.actualizarMiPerfil(authentication.getName(), request)));
    }

    @DeleteMapping("/me")
    public ResponseEntity<GenericResponse> desactivarMiCuenta(Authentication authentication) {
        clienteService.desactivarMiCuenta(authentication.getName());
        return ResponseEntity.ok(GenericResponse.ok("Cuenta desactivada correctamente.", null));
    }

    /** Único endpoint de consulta (solo ROLE_ADMIN): todos los filtros son opcionales y combinables. */
    @PostMapping("/buscar")
    public ResponseEntity<GenericResponse> buscar(@Valid @RequestBody ClienteBusquedaRequest criterios) {
        return ResponseEntity.ok(GenericResponse.ok("Consulta exitosa.", clienteService.buscar(criterios)));
    }
}
