package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.cliente.Cliente;
import com.proyecto.servicios.entity.cliente.Usuario;
import com.proyecto.servicios.model.cliente.UsuarioResponse;
import com.proyecto.servicios.repositorys.cliente.UsuarioRepository;
import com.proyecto.servicios.service.ClienteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceImplTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ClienteService clienteService;

    @InjectMocks
    private UsuarioServiceImpl usuarioService;

    @Test
    void usuarioInactivo_puedeConsultarSoloSuPropioPerfil() {
        UUID id = UUID.randomUUID();
        Usuario usuario = usuario(id, "inactive@example.com", false, true);
        when(usuarioRepository.findByIdUsuario(id)).thenReturn(Optional.of(usuario));

        UsuarioResponse response = usuarioService.obtenerPorId(id, "inactive@example.com");

        assertEquals("inactive@example.com", response.correo());
        assertFalse(response.activo());
    }

    @Test
    void usuarioInactivo_noPuedeCambiarSuPassword() {
        UUID id = UUID.randomUUID();
        Usuario usuario = usuario(id, "inactive@example.com", false, true);
        when(usuarioRepository.findByIdUsuario(id)).thenReturn(Optional.of(usuario));

        assertThrows(RuntimeException.class,
                () -> usuarioService.actualizarPassword(id, "inactive@example.com",
                        new com.proyecto.servicios.model.cliente.PasswordUpdateRequest(
                                "OldPass1!", "NewPass2@"
                        )));
    }

    @Test
    void usuarioNoPuedeConsultarPerfilDeOtroUsuario() {
        UUID id = UUID.randomUUID();
        when(usuarioRepository.findByIdUsuario(id))
                .thenReturn(Optional.of(usuario(id, "other@example.com", true, true)));

        assertThrows(AccessDeniedException.class,
                () -> usuarioService.obtenerPorId(id, "caller@example.com"));
    }

    @Test
    void usuarioInactivo_puedeReactivarseYNecesitaIniciarSesionDeNuevo() {
        UUID id = UUID.randomUUID();
        Usuario usuario = usuario(id, "inactive@example.com", false, true);
        when(usuarioRepository.findByIdUsuario(id)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UsuarioResponse response = usuarioService.reactivarPropio(id, "inactive@example.com");

        assertTrue(usuario.isActivo());
        assertTrue(response.activo());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void usuarioInactivoConClienteInactivo_reactivaClienteAntesDeLaCuenta() {
        UUID id = UUID.randomUUID();
        Usuario usuario = usuario(id, "inactive@example.com", false, false);
        when(usuarioRepository.findByIdUsuario(id)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        usuarioService.reactivarPropio(id, "inactive@example.com");

        verify(clienteService).reactivar(usuario.getCliente().getIdPersona());
        assertTrue(usuario.isActivo());
    }

    @Test
    void usuarioActivo_puedeDesactivarseSoloASiMismo() {
        UUID id = UUID.randomUUID();
        Usuario usuario = usuario(id, "active@example.com", true, true);
        when(usuarioRepository.findByIdUsuario(id)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocation -> invocation.getArgument(0));

        usuarioService.desactivarPropio(id, "active@example.com");

        assertFalse(usuario.isActivo());
        verify(usuarioRepository).save(usuario);
    }

    private Usuario usuario(UUID id, String correo, boolean activo, boolean clienteActivo) {
        Cliente cliente = new Cliente();
        cliente.setIdPersona(UUID.randomUUID());
        cliente.setActivo(clienteActivo);
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(id);
        usuario.setCorreo(correo);
        usuario.setActivo(activo);
        usuario.setCliente(cliente);
        cliente.setUsuario(usuario);
        return usuario;
    }
}
