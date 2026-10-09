package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.cliente.Usuario;
import com.proyecto.servicios.exception.AccesoNoAutorizadoException;
import com.proyecto.servicios.exception.ErrorValidacionException;
import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.model.cliente.PasswordUpdateRequest;
import com.proyecto.servicios.model.cliente.PasswordPolicy;
import com.proyecto.servicios.model.cliente.UsuarioResponse;
import com.proyecto.servicios.repositorys.cliente.UsuarioRepository;
import com.proyecto.servicios.service.UsuarioService;
import com.proyecto.servicios.service.ClienteService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class UsuarioServiceImpl implements UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final ClienteService clienteService;

    public UsuarioServiceImpl(UsuarioRepository usuarioRepository,
                              PasswordEncoder passwordEncoder,
                              ClienteService clienteService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.clienteService = clienteService;
    }

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponse obtenerPorId(UUID id, String correoAutenticado) {
        return toResponse(obtenerUsuarioPropio(id, correoAutenticado, true));
    }

    @Override
    public UsuarioResponse actualizarPassword(UUID id, String correoAutenticado, PasswordUpdateRequest request) {
        if (!PasswordPolicy.compatibleConBcrypt(request.passwordNueva())) {
            throw new ErrorValidacionException("La contraseña no puede superar 72 bytes en UTF-8 con BCrypt.");
        }
        Usuario usuario = obtenerUsuarioPropio(id, correoAutenticado, false);
        if (!passwordEncoder.matches(request.passwordActual(), usuario.getPassword())) {
            throw new AccesoNoAutorizadoException(
                    "CREDENCIALES_INVALIDAS", "La contraseña actual es incorrecta.", 401
            );
        }
        usuario.setPassword(passwordEncoder.encode(request.passwordNueva()));
        return toResponse(usuarioRepository.save(usuario));
    }

    @Override
    public void desactivarPropio(UUID id, String correoAutenticado) {
        Usuario usuario = obtenerUsuarioPropio(id, correoAutenticado, false);
        usuario.setActivo(false);
        usuarioRepository.save(usuario);
    }

    @Override
    public UsuarioResponse reactivarPropio(UUID id, String correoAutenticado) {
        Usuario usuario = obtenerUsuarioPropio(id, correoAutenticado, true);
        if (!usuario.getCliente().isActivo()) {
            clienteService.reactivar(usuario.getCliente().getIdPersona());
        }
        usuario.setActivo(true);
        return toResponse(usuarioRepository.save(usuario));
    }

    private Usuario obtenerUsuarioPropio(UUID id, String correoAutenticado, boolean permitirInactivo) {
        Usuario usuario = usuarioRepository.findByIdUsuario(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "USUARIO_NO_ENCONTRADO", "No se encontró el usuario solicitado."));
        if (!usuario.getCorreo().equalsIgnoreCase(correoAutenticado)) {
            throw new AccessDeniedException("Solo puede consultar o modificar sus propios datos.");
        }
        if (!permitirInactivo && (!usuario.isActivo() || !usuario.getCliente().isActivo())) {
            throw new AccesoNoAutorizadoException(
                    "USUARIO_INACTIVO", "Un usuario inactivo no puede realizar esta operación.", 403
            );
        }
        return usuario;
    }

    private UsuarioResponse toResponse(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getIdUsuario(),
                usuario.getCliente().getIdPersona(),
                usuario.getCorreo(),
                usuario.isActivo(),
                usuario.getFechaCreacion(),
                usuario.getFechaActualizacion()
        );
    }
}
