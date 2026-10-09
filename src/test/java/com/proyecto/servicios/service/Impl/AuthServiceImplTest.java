package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.cliente.Cliente;
import com.proyecto.servicios.entity.cliente.Usuario;
import com.proyecto.servicios.exception.AccesoNoAutorizadoException;
import com.proyecto.servicios.model.cliente.LoginRequest;
import com.proyecto.servicios.model.cliente.LoginResponse;
import com.proyecto.servicios.repositorys.cliente.UsuarioRepository;
import com.proyecto.servicios.service.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void login_emiteTokenParaUsuarioActivoConCredencialesValidas() {
        Usuario usuario = usuarioActivo();
        when(usuarioRepository.findByCorreoIgnoreCase("cliente@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Secreta1!", "hash")).thenReturn(true);
        when(jwtService.generarToken("cliente@example.com", false)).thenReturn("jwt-token");
        when(jwtService.getExpiracionEnSegundos()).thenReturn(3600L);

        LoginResponse response = authService.login(new LoginRequest("cliente@example.com", "Secreta1!"));

        assertEquals("jwt-token", response.token());
        assertEquals("Bearer", response.tipo());
        assertEquals(3600, response.expiraEnSegundos());
        assertEquals("FULL", response.modoAcceso());
    }

    @Test
    void login_rechazaPasswordIncorrecto() {
        Usuario usuario = usuarioActivo();
        when(usuarioRepository.findByCorreoIgnoreCase("cliente@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("incorrecta", "hash")).thenReturn(false);

        AccesoNoAutorizadoException exception = assertThrows(AccesoNoAutorizadoException.class,
                () -> authService.login(new LoginRequest("cliente@example.com", "incorrecta")));
        assertEquals("CREDENCIALES_INVALIDAS", exception.getCodigo());
    }

    @Test
    void loginUsuarioInactivo_emiteTokenDeRecuperacion() {
        Usuario usuario = usuarioActivo();
        usuario.setActivo(false);
        when(usuarioRepository.findByCorreoIgnoreCase("cliente@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Secreta1!", "hash")).thenReturn(true);
        when(jwtService.generarToken("cliente@example.com", true)).thenReturn("recovery-token");
        when(jwtService.getExpiracionEnSegundos()).thenReturn(3600L);

        LoginResponse response = authService.login(new LoginRequest("cliente@example.com", "Secreta1!"));

        assertEquals("recovery-token", response.token());
        assertEquals("RECOVERY", response.modoAcceso());
    }

    @Test
    void loginUsuarioInactivo_rechazaPasswordIncorrecto() {
        Usuario usuario = usuarioActivo();
        usuario.setActivo(false);
        when(usuarioRepository.findByCorreoIgnoreCase("cliente@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("incorrecta", "hash")).thenReturn(false);

        AccesoNoAutorizadoException exception = assertThrows(AccesoNoAutorizadoException.class,
                () -> authService.login(new LoginRequest("cliente@example.com", "incorrecta")));
        assertEquals("CREDENCIALES_INVALIDAS", exception.getCodigo());
    }

    private Usuario usuarioActivo() {
        Cliente cliente = new Cliente();
        cliente.setActivo(true);
        Usuario usuario = new Usuario();
        usuario.setCliente(cliente);
        usuario.setCorreo("cliente@example.com");
        usuario.setPassword("hash");
        usuario.setActivo(true);
        return usuario;
    }
}
