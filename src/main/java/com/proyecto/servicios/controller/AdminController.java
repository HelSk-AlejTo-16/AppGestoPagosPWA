package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.admin.AdminAbonoCuentaRequest;
import com.proyecto.servicios.model.admin.AdminCambiarEstadoRequest;
import com.proyecto.servicios.model.admin.AdminCambiarRolRequest;
import com.proyecto.servicios.model.admin.AdminUsuarioBusquedaRequest;
import com.proyecto.servicios.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/resumen")
    public ResponseEntity<GenericResponse> obtenerResumen() {
        return ResponseEntity.ok(GenericResponse.ok(
                "Resumen administrativo obtenido.",
                adminService.obtenerResumen()
        ));
    }

    @PostMapping("/usuarios/buscar")
    public ResponseEntity<GenericResponse> buscarUsuarios(
            @Valid @RequestBody(required = false) AdminUsuarioBusquedaRequest request
    ) {
        return ResponseEntity.ok(GenericResponse.ok(
                "Consulta de usuarios exitosa.",
                adminService.buscarUsuarios(request)
        ));
    }

    @PatchMapping("/usuarios/{idCliente}/estado")
    public ResponseEntity<GenericResponse> cambiarEstadoUsuario(
            @PathVariable UUID idCliente,
            @Valid @RequestBody AdminCambiarEstadoRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(GenericResponse.ok(
                "Estado de usuario actualizado.",
                adminService.cambiarEstadoUsuario(idCliente, request.activo(), authentication.getName())
        ));
    }

    @PatchMapping("/usuarios/{idCliente}/rol")
    public ResponseEntity<GenericResponse> cambiarRolUsuario(
            @PathVariable UUID idCliente,
            @Valid @RequestBody AdminCambiarRolRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(GenericResponse.ok(
                "Rol de usuario actualizado.",
                adminService.cambiarRolUsuario(idCliente, request.rol(), authentication.getName())
        ));
    }

    @PostMapping("/usuarios/{idCliente}/desbloquear")
    public ResponseEntity<GenericResponse> desbloquearUsuario(@PathVariable UUID idCliente) {
        return ResponseEntity.ok(GenericResponse.ok(
                "Usuario desbloqueado correctamente.",
                adminService.desbloquearUsuario(idCliente)
        ));
    }

    @PostMapping("/usuarios/{idCliente}/cerrar-sesion")
    public ResponseEntity<GenericResponse> forzarCierreSesion(@PathVariable UUID idCliente) {
        return ResponseEntity.ok(GenericResponse.ok(
                "Sesión de usuario cerrada correctamente.",
                adminService.forzarCierreSesion(idCliente)
        ));
    }

    @PostMapping("/cuentas/abonar")
    public ResponseEntity<GenericResponse> abonarSaldoACuenta(
            @Valid @RequestBody AdminAbonoCuentaRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(GenericResponse.ok(
                "Abono administrativo realizado correctamente.",
                adminService.abonarSaldoACuenta(request, authentication.getName())
        ));
    }
}
