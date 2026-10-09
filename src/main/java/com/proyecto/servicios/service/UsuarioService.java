package com.proyecto.servicios.service;

import com.proyecto.servicios.model.cliente.PasswordUpdateRequest;
import com.proyecto.servicios.model.cliente.UsuarioResponse;

import java.util.UUID;

public interface UsuarioService {

    UsuarioResponse obtenerPorId(UUID id, String correoAutenticado);

    UsuarioResponse actualizarPassword(UUID id, String correoAutenticado, PasswordUpdateRequest request);

    void desactivarPropio(UUID id, String correoAutenticado);

    UsuarioResponse reactivarPropio(UUID id, String correoAutenticado);
}
