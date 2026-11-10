package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.cliente.Cliente;
import com.proyecto.servicios.entity.cliente.Usuario;
import com.proyecto.servicios.exception.AccesoNoAutorizadoException;
import com.proyecto.servicios.exception.BusinessConflictException;
import com.proyecto.servicios.model.cliente.LoginRequest;
import com.proyecto.servicios.model.cliente.LoginResponse;
import com.proyecto.servicios.model.cliente.Verificar2faRequest;
import com.proyecto.servicios.repositorys.cliente.UsuarioRepository;
import com.proyecto.servicios.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
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
    @DisplayName("Paso 1 Login: Genera ticket temporal y código 2FA de 6 dígitos")
    void login_generaTicket2FAConCredencialesValidas() {
        Usuario usuario = usuarioActivo();
        when(usuarioRepository.findByCorreoIgnoreCase("cliente@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Secreta1!", "hash")).thenReturn(true);
        when(jwtService.generarTicket2FA("cliente@example.com", "MOVIL")).thenReturn("ticket-2fa-temp");

        LoginResponse response = authService.login(new LoginRequest("cliente@example.com", "Secreta1!", "MOVIL"));

        assertTrue(response.requiere2fa());
        assertEquals("ticket-2fa-temp", response.token());
        assertEquals("2FA_TICKET", response.tipo());
        assertEquals("2FA_PENDING", response.modoAcceso());
        assertNotNull(response.codigoOtpSimulado());
        assertEquals(6, response.codigoOtpSimulado().length());
        assertNotNull(usuario.getCodigo2fa());
        assertNotNull(usuario.getExpiracion2fa());
    }

    @Test
    @DisplayName("Paso 1 Login: Detecta si existe sesión activa previa en otra plataforma")
    void login_detectaSesionActivaPrevia() {
        Usuario usuario = usuarioActivo();
        usuario.setIdSesionActiva("sesion-previa-movil");
        usuario.setPlataformaActiva("MOVIL");

        when(usuarioRepository.findByCorreoIgnoreCase("cliente@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Secreta1!", "hash")).thenReturn(true);
        when(jwtService.generarTicket2FA("cliente@example.com", "WEB")).thenReturn("ticket-2fa-web");

        LoginResponse response = authService.login(new LoginRequest("cliente@example.com", "Secreta1!", "WEB"));

        assertTrue(response.requiere2fa());
        assertTrue(response.sesionActivaPrevia());
        assertEquals("MOVIL", response.plataformaPrevia());
        assertTrue(response.mensajeSesion().contains("MOVIL"));
    }

    @Test
    @DisplayName("Paso 1 Login: Rechaza contraseña incorrecta y suma intentos fallidos")
    void login_rechazaPasswordIncorrecto() {
        Usuario usuario = usuarioActivo();
        when(usuarioRepository.findByCorreoIgnoreCase("cliente@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("incorrecta", "hash")).thenReturn(false);

        AccesoNoAutorizadoException exception = assertThrows(AccesoNoAutorizadoException.class,
                () -> authService.login(new LoginRequest("cliente@example.com", "incorrecta")));
        assertEquals("CREDENCIALES_INVALIDAS", exception.getCodigo());
        assertEquals(1, usuario.getIntentosFallidos());
    }

    @Test
    @DisplayName("Paso 2 Verificar 2FA: Con código correcto entrega token JWT final y registra sesión única")
    void verificar2fa_exitoso() {
        Usuario usuario = usuarioActivo();
        usuario.setCodigo2fa("123456");
        usuario.setExpiracion2fa(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));

        when(jwtService.esToken2FA("ticket-2fa")).thenReturn(true);
        when(jwtService.extraerCorreo("ticket-2fa")).thenReturn("cliente@example.com");
        when(jwtService.extraerPlataforma("ticket-2fa")).thenReturn("MOVIL");
        when(usuarioRepository.findByCorreoIgnoreCase("cliente@example.com")).thenReturn(Optional.of(usuario));
        when(jwtService.generarTokenConSesion(eq("cliente@example.com"), eq(false), anyString(), eq("MOVIL")))
                .thenReturn("token-final-jwt");
        when(jwtService.getExpiracionEnSegundos()).thenReturn(3600L);

        LoginResponse response = authService.verificar2fa(new Verificar2faRequest("ticket-2fa", "123456", false));

        assertFalse(response.requiere2fa());
        assertEquals("token-final-jwt", response.token());
        assertEquals("FULL", response.modoAcceso());
        assertNotNull(usuario.getIdSesionActiva());
        assertEquals("MOVIL", usuario.getPlataformaActiva());
        assertNull(usuario.getCodigo2fa());
    }

    @Test
    @DisplayName("Paso 2 Verificar 2FA: Si hay sesión previa y no se forzó cierre, arroja BusinessConflictException")
    void verificar2fa_conflictoSesionPreviaSinForzar() {
        Usuario usuario = usuarioActivo();
        usuario.setCodigo2fa("123456");
        usuario.setExpiracion2fa(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));
        usuario.setIdSesionActiva("sesion-movil");
        usuario.setPlataformaActiva("MOVIL");

        when(jwtService.esToken2FA("ticket-web")).thenReturn(true);
        when(jwtService.extraerCorreo("ticket-web")).thenReturn("cliente@example.com");
        when(jwtService.extraerPlataforma("ticket-web")).thenReturn("WEB");
        when(usuarioRepository.findByCorreoIgnoreCase("cliente@example.com")).thenReturn(Optional.of(usuario));

        BusinessConflictException ex = assertThrows(BusinessConflictException.class, () ->
                authService.verificar2fa(new Verificar2faRequest("ticket-web", "123456", false)));

        assertEquals("SESION_ACTIVA_PREVIA", ex.getCodigo());
        assertTrue(ex.getMessage().contains("MOVIL"));
    }

    @Test
    @DisplayName("Paso 2 Verificar 2FA: Si forzarCierre es true, sustituye la sesión anterior por la nueva")
    void verificar2fa_forzarCierreSustituyeSesion() {
        Usuario usuario = usuarioActivo();
        usuario.setCodigo2fa("123456");
        usuario.setExpiracion2fa(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));
        usuario.setIdSesionActiva("sesion-movil-antigua");
        usuario.setPlataformaActiva("MOVIL");

        when(jwtService.esToken2FA("ticket-web")).thenReturn(true);
        when(jwtService.extraerCorreo("ticket-web")).thenReturn("cliente@example.com");
        when(jwtService.extraerPlataforma("ticket-web")).thenReturn("WEB");
        when(usuarioRepository.findByCorreoIgnoreCase("cliente@example.com")).thenReturn(Optional.of(usuario));
        when(jwtService.generarTokenConSesion(eq("cliente@example.com"), eq(false), anyString(), eq("WEB")))
                .thenReturn("nuevo-token-web");
        when(jwtService.getExpiracionEnSegundos()).thenReturn(3600L);

        LoginResponse response = authService.verificar2fa(new Verificar2faRequest("ticket-web", "123456", true));

        assertEquals("nuevo-token-web", response.token());
        assertEquals("WEB", usuario.getPlataformaActiva());
        assertNotEquals("sesion-movil-antigua", usuario.getIdSesionActiva());
    }

    @Test
    @DisplayName("Paso 2 Verificar 2FA: Rechaza código incorrecto")
    void verificar2fa_codigoIncorrecto() {
        Usuario usuario = usuarioActivo();
        usuario.setCodigo2fa("123456");
        usuario.setExpiracion2fa(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));

        when(jwtService.esToken2FA("ticket-2fa")).thenReturn(true);
        when(jwtService.extraerCorreo("ticket-2fa")).thenReturn("cliente@example.com");
        when(usuarioRepository.findByCorreoIgnoreCase("cliente@example.com")).thenReturn(Optional.of(usuario));

        AccesoNoAutorizadoException ex = assertThrows(AccesoNoAutorizadoException.class, () ->
                authService.verificar2fa(new Verificar2faRequest("ticket-2fa", "999999", false)));

        assertEquals("CODIGO_INVALIDO", ex.getCodigo());
    }

    @Test
    @DisplayName("Logout: Borra la sesión activa en BD")
    void logout_limpiaSesion() {
        Usuario usuario = usuarioActivo();
        usuario.setIdSesionActiva("sesion-123");
        usuario.setPlataformaActiva("MOVIL");

        when(usuarioRepository.findByCorreoIgnoreCase("cliente@example.com")).thenReturn(Optional.of(usuario));

        authService.logout("cliente@example.com");

        assertNull(usuario.getIdSesionActiva());
        assertNull(usuario.getPlataformaActiva());
        verify(usuarioRepository).save(usuario);
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
