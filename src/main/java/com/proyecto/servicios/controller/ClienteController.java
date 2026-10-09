package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.cliente.ClienteActualizarRequest;
import com.proyecto.servicios.model.cliente.ClienteRegistroRequest;
import com.proyecto.servicios.model.cliente.ClienteResponse;
import com.proyecto.servicios.model.cliente.PaginaResponse;
import com.proyecto.servicios.service.ClienteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.validation.annotation.Validated;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/clientes")
public class ClienteController {

    private final ClienteService clienteService;

    public ClienteController(ClienteService clienteService) {
        this.clienteService = clienteService;
    }

    @PostMapping
    @Operation(security = {})
    public ResponseEntity<ClienteResponse> registrar(@Valid @RequestBody ClienteRegistroRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.registrar(request));
    }

    @GetMapping
    public PaginaResponse<ClienteResponse> obtenerTodos(
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return clienteService.obtenerTodos(pagina, tamano);
    }

    @GetMapping("/activos")
    public PaginaResponse<ClienteResponse> obtenerActivos(
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return clienteService.obtenerActivos(pagina, tamano);
    }

    @GetMapping("/registrados")
    public PaginaResponse<ClienteResponse> obtenerRegistrados(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamano) {
        return clienteService.obtenerRegistrados(desde, hasta, pagina, tamano);
    }

    @GetMapping("/curp/{curp}")
    public ClienteResponse obtenerPorCurp(@PathVariable String curp) {
        return clienteService.obtenerPorCurp(curp);
    }

    @GetMapping("/rfc/{rfc}")
    public ClienteResponse obtenerPorRfc(@PathVariable String rfc) {
        return clienteService.obtenerPorRfc(rfc);
    }

    @GetMapping("/correo/{correo}")
    public ClienteResponse obtenerPorCorreo(@PathVariable String correo) {
        return clienteService.obtenerPorCorreo(correo);
    }

    @GetMapping("/cuenta/{numeroCuenta}")
    public ClienteResponse obtenerPorNumeroCuenta(@PathVariable String numeroCuenta) {
        return clienteService.obtenerPorNumeroCuenta(numeroCuenta);
    }

    @GetMapping("/{id}")
    public ClienteResponse obtenerPorId(@PathVariable UUID id) {
        return clienteService.obtenerPorId(id);
    }

    @PutMapping("/{id}")
    public ClienteResponse actualizar(@PathVariable UUID id,
                                      Authentication authentication,
                                      @Valid @RequestBody ClienteActualizarRequest request) {
        return clienteService.actualizar(id, authentication.getName(), request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable UUID id, Authentication authentication) {
        clienteService.desactivar(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
