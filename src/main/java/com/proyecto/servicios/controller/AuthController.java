package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.cliente.LoginRequest;
import com.proyecto.servicios.model.cliente.LoginResponse;
import com.proyecto.servicios.model.cliente.Reenviar2faRequest;
import com.proyecto.servicios.model.cliente.RestablecerPasswordRequest;
import com.proyecto.servicios.model.cliente.SolicitarRecuperacionRequest;
import com.proyecto.servicios.model.cliente.Verificar2faRequest;
import com.proyecto.servicios.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Paso 1: Validación de credenciales e inicio de 2FA", security = {})
    public ResponseEntity<GenericResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(GenericResponse.ok("Credenciales verificadas. Ingrese el código de autenticación 2FA.", response));
    }

    @PostMapping("/2fa/verificar")
    @Operation(summary = "Paso 2: Validación de código 2FA y resolución de sesión única", security = {})
    public ResponseEntity<GenericResponse> verificar2fa(@Valid @RequestBody Verificar2faRequest request) {
        LoginResponse response = authService.verificar2fa(request);
        return ResponseEntity.ok(GenericResponse.ok("Autenticación completada exitosamente.", response));
    }

    @PostMapping("/2fa/reenviar")
    @Operation(summary = "Reenvío de código 2FA", security = {})
    public ResponseEntity<GenericResponse> reenviar2fa(@Valid @RequestBody Reenviar2faRequest request) {
        LoginResponse response = authService.reenviar2fa(request);
        return ResponseEntity.ok(GenericResponse.ok("Nuevo código de verificación generado exitosamente.", response));
    }

    @PostMapping("/password/solicitar-codigo")
    @Operation(summary = "Solicitar código OTP al correo para recuperar contraseña", security = {})
    public ResponseEntity<GenericResponse> solicitarCodigoPassword(
            @Valid @RequestBody SolicitarRecuperacionRequest request) {
        authService.solicitarRecuperacionPassword(request);
        return ResponseEntity.ok(GenericResponse.ok(
                "Hemos enviado un código de recuperación de 6 dígitos a tu correo electrónico.", null));
    }

    @PostMapping("/password/restablecer")
    @Operation(summary = "Restablecer contraseña validando el código OTP enviado al correo", security = {})
    public ResponseEntity<GenericResponse> restablecerPassword(
            @Valid @RequestBody RestablecerPasswordRequest request) {
        authService.restablecerPassword(request);
        return ResponseEntity.ok(GenericResponse.ok(
                "Contraseña restablecida correctamente. Ya puedes iniciar sesión.", null));
    }

    @PostMapping("/logout")
    @Operation(summary = "Cierre seguro de sesión activa")
    public ResponseEntity<GenericResponse> logout(Authentication authentication) {
        authService.logout(authentication != null ? authentication.getName() : null);
        return ResponseEntity.ok(GenericResponse.ok("Sesión cerrada exitosamente.", null));
    }
}
