package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.cliente.PasswordUpdateRequest;
import com.proyecto.servicios.model.cliente.UsuarioResponse;
import com.proyecto.servicios.model.cliente.ClienteResponse;
import com.proyecto.servicios.service.ClienteService;
import com.proyecto.servicios.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final ClienteService clienteService;

    public UsuarioController(UsuarioService usuarioService, ClienteService clienteService) {
        this.usuarioService = usuarioService;
        this.clienteService = clienteService;
    }

    @GetMapping("/{id}")
    public UsuarioResponse obtenerPorId(@PathVariable UUID id, Authentication authentication) {
        return usuarioService.obtenerPorId(id, authentication.getName());
    }

    @GetMapping("/{id}/perfil")
    public ClienteResponse obtenerPerfil(@PathVariable UUID id, Authentication authentication) {
        return clienteService.obtenerPorIdPropio(id, authentication.getName());
    }

    @PutMapping("/{id}/password")
    public UsuarioResponse actualizarPassword(@PathVariable UUID id,
                                              Authentication authentication,
                                              @Valid @RequestBody PasswordUpdateRequest request) {
        return usuarioService.actualizarPassword(id, authentication.getName(), request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivarPropio(@PathVariable UUID id, Authentication authentication) {
        usuarioService.desactivarPropio(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/reactivar")
    public UsuarioResponse reactivarPropio(@PathVariable UUID id, Authentication authentication) {
        return usuarioService.reactivarPropio(id, authentication.getName());
    }
}
