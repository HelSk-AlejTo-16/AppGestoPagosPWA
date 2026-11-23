package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.cliente.Cliente;
import com.proyecto.servicios.entity.cliente.Usuario;
import com.proyecto.servicios.exception.AccesoNoAutorizadoException;
import com.proyecto.servicios.exception.BusinessConflictException;
import com.proyecto.servicios.exception.ErrorValidacionException;
import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.model.cliente.LoginRequest;
import com.proyecto.servicios.model.cliente.LoginResponse;
import com.proyecto.servicios.model.cliente.Reenviar2faRequest;
import com.proyecto.servicios.model.cliente.RestablecerPasswordRequest;
import com.proyecto.servicios.model.cliente.SolicitarRecuperacionRequest;
import com.proyecto.servicios.model.cliente.Verificar2faRequest;
import com.proyecto.servicios.repositorys.cliente.UsuarioRepository;
import com.proyecto.servicios.service.EmailService;
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

    @Mock
    private EmailService emailService;

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
        assertNull(response.codigoOtpSimulado());
        assertNotNull(usuario.getCodigo2fa());
        assertEquals(6, usuario.getCodigo2fa().length());
        assertNotNull(usuario.getExpiracion2fa());
    }

    @Test
    @DisplayName("Paso 1 Login: Oculta codigoOtpSimulado cuando el correo 2FA se envía exitosamente")
    void login_ocultaCodigoSimuladoCuandoCorreoSeEnviaExitosamente() {
        Usuario usuario = usuarioActivo();
        when(usuarioRepository.findByCorreoIgnoreCase("cliente@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Secreta1!", "hash")).thenReturn(true);
        when(jwtService.generarTicket2FA("cliente@example.com", "ESCRITORIO")).thenReturn("ticket-2fa-pc");
        when(emailService.enviarCodigo2fa(eq("cliente@example.com"), anyString(), eq("ESCRITORIO")))
                .thenReturn(true);

        LoginResponse response = authService.login(new LoginRequest("cliente@example.com", "Secreta1!", "ESCRITORIO"));

        assertTrue(response.requiere2fa());
        assertNull(response.codigoOtpSimulado());
        verify(emailService).enviarCodigo2fa(eq("cliente@example.com"), anyString(), eq("ESCRITORIO"));
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
        assertEquals(1, usuario.getIntentosFallidos());
    }

    @Test
    @DisplayName("Paso 2 Verificar 2FA: Bloquea la cuenta e invalida el OTP al tercer intento fallido")
    void verificar2fa_bloqueaTrasTresIntentos() {
        Usuario usuario = usuarioActivo();
        usuario.setCodigo2fa("123456");
        usuario.setExpiracion2fa(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));
        usuario.setIntentosFallidos(2);

        when(jwtService.esToken2FA("ticket-2fa")).thenReturn(true);
        when(jwtService.extraerCorreo("ticket-2fa")).thenReturn("cliente@example.com");
        when(usuarioRepository.findByCorreoIgnoreCase("cliente@example.com")).thenReturn(Optional.of(usuario));

        AccesoNoAutorizadoException ex = assertThrows(AccesoNoAutorizadoException.class, () ->
                authService.verificar2fa(new Verificar2faRequest("ticket-2fa", "000000", false)));

        assertEquals("CODIGO_BLOQUEADO", ex.getCodigo());
        assertEquals(403, ex.getStatus());
        assertNull(usuario.getCodigo2fa());
        assertNull(usuario.getExpiracion2fa());
        assertNotNull(usuario.getFechaBloqueo());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    @DisplayName("Reenviar 2FA: Genera nuevo código cuando ya pasó el enfriamiento de 30s")
    void reenviar2fa_exitosoTrasCooldown() {
        Usuario usuario = usuarioActivo();
        usuario.setExpiracion2fa(OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(200));

        when(jwtService.esToken2FA("ticket-2fa")).thenReturn(true);
        when(jwtService.extraerCorreo("ticket-2fa")).thenReturn("cliente@example.com");
        when(jwtService.extraerPlataforma("ticket-2fa")).thenReturn("MOVIL");
        when(usuarioRepository.findByCorreoIgnoreCase("cliente@example.com")).thenReturn(Optional.of(usuario));
        when(jwtService.generarTicket2FA("cliente@example.com", "MOVIL")).thenReturn("nuevo-ticket-2fa");
        when(emailService.enviarCodigo2fa(eq("cliente@example.com"), anyString(), eq("MOVIL"))).thenReturn(true);

        LoginResponse response = authService.reenviar2fa(new Reenviar2faRequest("ticket-2fa"));

        assertTrue(response.requiere2fa());
        assertEquals("nuevo-ticket-2fa", response.token());
        assertNull(response.codigoOtpSimulado());
        assertEquals(0, usuario.getIntentosFallidos());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    @DisplayName("Reenviar 2FA: Rechaza con HTTP 429 si no han pasado 30 segundos")
    void reenviar2fa_rechazaDentroDeCooldown() {
        Usuario usuario = usuarioActivo();
        usuario.setExpiracion2fa(OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(290));

        when(jwtService.esToken2FA("ticket-2fa")).thenReturn(true);
        when(jwtService.extraerCorreo("ticket-2fa")).thenReturn("cliente@example.com");
        when(jwtService.extraerPlataforma("ticket-2fa")).thenReturn("MOVIL");
        when(usuarioRepository.findByCorreoIgnoreCase("cliente@example.com")).thenReturn(Optional.of(usuario));

        AccesoNoAutorizadoException ex = assertThrows(AccesoNoAutorizadoException.class, () ->
                authService.reenviar2fa(new Reenviar2faRequest("ticket-2fa")));

        assertEquals("REENVIO_DEMASIADO_PRONTO", ex.getCodigo());
        assertEquals(429, ex.getStatus());
    }

    @Test
    @DisplayName("Recuperación de contraseña: Envía código OTP al correo registrado")
    void solicitarRecuperacionPassword_exitoso() {
        Usuario usuario = usuarioActivo();
        usuario.getCliente().setNombre("Ana");

        when(usuarioRepository.findByCorreoIgnoreCase("cliente@example.com")).thenReturn(Optional.of(usuario));
        when(emailService.enviarCodigoRecuperacionPassword(eq("cliente@example.com"), eq("Ana"), anyString()))
                .thenReturn(true);

        authService.solicitarRecuperacionPassword(new SolicitarRecuperacionRequest("cliente@example.com"));

        assertNotNull(usuario.getCodigo2fa());
        assertEquals(6, usuario.getCodigo2fa().length());
        assertNotNull(usuario.getExpiracion2fa());
        verify(usuarioRepository).save(usuario);
        verify(emailService).enviarCodigoRecuperacionPassword(eq("cliente@example.com"), eq("Ana"), anyString());
    }

    @Test
    @DisplayName("Recuperación de contraseña: Falla con 404 si el correo no existe")
    void solicitarRecuperacionPassword_correoInexistente() {
        when(usuarioRepository.findByCorreoIgnoreCase("noexiste@example.com")).thenReturn(Optional.empty());

        RecursoNoEncontradoException ex = assertThrows(RecursoNoEncontradoException.class, () ->
                authService.solicitarRecuperacionPassword(new SolicitarRecuperacionRequest("noexiste@example.com")));

        assertEquals("USUARIO_NO_ENCONTRADO", ex.getCodigo());
    }

    @Test
    @DisplayName("Restablecer contraseña: Actualiza clave con código OTP válido y cierra sesión previa")
    void restablecerPassword_exitoso() {
        Usuario usuario = usuarioActivo();
        usuario.setCodigo2fa("654321");
        usuario.setExpiracion2fa(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));
        usuario.setIdSesionActiva("sesion-vieja");
        usuario.setPlataformaActiva("WEB");

        when(usuarioRepository.findByCorreoIgnoreCase("cliente@example.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.encode("NuevaClave1!")).thenReturn("nuevo-hash");

        authService.restablecerPassword(new RestablecerPasswordRequest("cliente@example.com", "654321", "NuevaClave1!"));

        assertEquals("nuevo-hash", usuario.getPassword());
        assertNull(usuario.getCodigo2fa());
        assertNull(usuario.getExpiracion2fa());
        assertNull(usuario.getIdSesionActiva());
        assertNull(usuario.getPlataformaActiva());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    @DisplayName("Restablecer contraseña: Rechaza contraseñas mayores a 72 bytes para BCrypt")
    void restablecerPassword_rechazaMayor72Bytes() {
        String claveLarga = "A1!" + "a".repeat(75);
        assertThrows(ErrorValidacionException.class, () ->
                authService.restablecerPassword(new RestablecerPasswordRequest("cliente@example.com", "654321", claveLarga)));
    }

    @Test
    @DisplayName("Restablecer contraseña: Rechaza código OTP incorrecto")
    void restablecerPassword_codigoIncorrecto() {
        Usuario usuario = usuarioActivo();
        usuario.setCodigo2fa("654321");
        usuario.setExpiracion2fa(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));

        when(usuarioRepository.findByCorreoIgnoreCase("cliente@example.com")).thenReturn(Optional.of(usuario));

        AccesoNoAutorizadoException ex = assertThrows(AccesoNoAutorizadoException.class, () ->
                authService.restablecerPassword(new RestablecerPasswordRequest("cliente@example.com", "111111", "NuevaClave1!")));

        assertEquals("CODIGO_INVALIDO", ex.getCodigo());
        assertEquals(1, usuario.getIntentosFallidos());
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
