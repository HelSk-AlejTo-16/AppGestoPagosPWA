package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.cliente.PasswordUpdateRequest;
import com.proyecto.servicios.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Todas las operaciones actúan sobre el usuario autenticado (identidad tomada del JWT),
 * por lo que no existen identificadores en la URL que puedan manipularse.
 */
@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/me")
    public ResponseEntity<GenericResponse> obtenerMiUsuario(Authentication authentication) {
        return ResponseEntity.ok(GenericResponse.ok("Consulta exitosa.",
                usuarioService.obtenerMiUsuario(authentication.getName())));
    }

    @PutMapping("/me/password")
    public ResponseEntity<GenericResponse> actualizarPassword(Authentication authentication,
                                                              @Valid @RequestBody PasswordUpdateRequest request) {
        return ResponseEntity.ok(GenericResponse.ok("Contraseña actualizada correctamente.",
                usuarioService.actualizarMiPassword(authentication.getName(), request)));
    }

    @DeleteMapping("/me")
    public ResponseEntity<GenericResponse> desactivarMiUsuario(Authentication authentication) {
        usuarioService.desactivarMiUsuario(authentication.getName());
        return ResponseEntity.ok(GenericResponse.ok("Usuario desactivado correctamente.", null));
    }

    @PutMapping("/me/reactivar")
    public ResponseEntity<GenericResponse> reactivarMiUsuario(Authentication authentication) {
        return ResponseEntity.ok(GenericResponse.ok("Usuario reactivado correctamente.",
                usuarioService.reactivarMiUsuario(authentication.getName())));
    }
}
