package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.cliente.Usuario;
import com.proyecto.servicios.exception.AccesoNoAutorizadoException;
import com.proyecto.servicios.exception.BusinessConflictException;
import com.proyecto.servicios.exception.ErrorValidacionException;
import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.model.cliente.LoginRequest;
import com.proyecto.servicios.model.cliente.LoginResponse;
import com.proyecto.servicios.model.cliente.PasswordPolicy;
import com.proyecto.servicios.model.cliente.Reenviar2faRequest;
import com.proyecto.servicios.model.cliente.RestablecerPasswordRequest;
import com.proyecto.servicios.model.cliente.SolicitarRecuperacionRequest;
import com.proyecto.servicios.model.cliente.Verificar2faRequest;
import com.proyecto.servicios.repositorys.cliente.UsuarioRepository;
import com.proyecto.servicios.service.AuthService;
import com.proyecto.servicios.service.EmailService;
import com.proyecto.servicios.service.JwtService;
import com.proyecto.servicios.util.InputSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    // Hash BCrypt precalculado para mitigar ataques de enumeración de correos por diferencia de tiempo
    private static final String DUMMY_BCRYPT_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoOHi6M1W8eQ0vK4b2G5pY2s9N3uZ6a8Wy";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthServiceImpl(UsuarioRepository usuarioRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService,
                           EmailService emailService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
    }

    @Override
    @Transactional(noRollbackFor = AccesoNoAutorizadoException.class)
    public LoginResponse login(LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new AccesoNoAutorizadoException(
                    "CREDENCIALES_INVALIDAS", "Correo electrónico o contraseña incorrectos.", 401
            );
        }

        String correoLimpio = InputSanitizer.sanitizarTexto(request.correo());
        if (correoLimpio == null || InputSanitizer.contieneCargaMaliciosa(correoLimpio)) {
            throw new AccesoNoAutorizadoException(
                    "CREDENCIALES_INVALIDAS", "Correo electrónico o contraseña incorrectos.", 401
            );
        }

        Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(correoLimpio).orElse(null);
        if (usuario == null) {
            // Igualar tiempo de cómputo BCrypt para evitar enumeración de usuarios por timing attack
            passwordEncoder.matches(request.password(), DUMMY_BCRYPT_HASH);
            throw new AccesoNoAutorizadoException(
                    "CREDENCIALES_INVALIDAS", "Correo electrónico o contraseña incorrectos.", 401
            );
        }

        // Verificar si la cuenta está bloqueada temporalmente por intentos fallidos
        verificarBloqueoTemporal(usuario);

        if (!passwordEncoder.matches(request.password(), usuario.getPassword())) {
            int intentos = usuario.getIntentosFallidos() + 1;
            usuario.setIntentosFallidos(intentos);

            if (intentos >= 3) {
                usuario.setFechaBloqueo(OffsetDateTime.now(ZoneOffset.UTC));
                usuarioRepository.save(usuario);
                throw new AccesoNoAutorizadoException(
                        "CUENTA_BLOQUEADA", "Ha superado el número de intentos permitidos. Cuenta bloqueada por 15 minutos.", 403
                );
            }
            usuarioRepository.save(usuario);
            throw new AccesoNoAutorizadoException(
                    "CREDENCIALES_INVALIDAS", "Correo electrónico o contraseña incorrectos.", 401
            );
        }

        // Restablecer contador de intentos en caso de credenciales correctas
        usuario.setIntentosFallidos(0);
        usuario.setFechaBloqueo(null);

        String plataforma = request.obtenerPlataformaNormalizada();
        String codigoOtp = generarCodigoOtp();
        usuario.setCodigo2fa(codigoOtp);
        usuario.setExpiracion2fa(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));
        usuarioRepository.save(usuario);

        log.info("[2FA GENERADO] Código OTP emitido para {} (Plataforma solicitante: {})",
                InputSanitizer.sanitizarParaLog(usuario.getCorreo()),
                InputSanitizer.sanitizarParaLog(plataforma));

        boolean correoEnviado = emailService.enviarCodigo2fa(usuario.getCorreo(), codigoOtp, plataforma);
        if (!correoEnviado) {
            log.warn("No fue posible enviar el correo 2FA a {}; no se expondrá el código OTP en la respuesta.", usuario.getCorreo());
        }

        boolean sesionActivaPrevia = usuario.getIdSesionActiva() != null
                && usuario.getPlataformaActiva() != null
                && !usuario.getPlataformaActiva().equalsIgnoreCase(plataforma);

        String ticketTemporal = jwtService.generarTicket2FA(usuario.getCorreo(), plataforma);

        return LoginResponse.para2fa(
                ticketTemporal,
                300,
                sesionActivaPrevia,
                usuario.getPlataformaActiva(),
                null
        );
    }

    @Override
    @Transactional(noRollbackFor = AccesoNoAutorizadoException.class)
    public LoginResponse verificar2fa(Verificar2faRequest request) {
        if (!jwtService.esToken2FA(request.ticketTemporal())) {
            throw new AccesoNoAutorizadoException("TICKET_INVALIDO", "El ticket de verificación 2FA no es válido o ha expirado.", 401);
        }

        String correo = jwtService.extraerCorreo(request.ticketTemporal());
        String plataforma = jwtService.extraerPlataforma(request.ticketTemporal());
        if (plataforma == null || plataforma.isBlank()) {
            plataforma = "MOVIL";
        }

        Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(correo)
                .orElseThrow(() -> new AccesoNoAutorizadoException("USUARIO_NO_ENCONTRADO", "Usuario no encontrado.", 401));

        validarCodigoOtpConBloqueo(usuario, request.codigoOtp());

        // Control de Sesión Única Concurrente (Web vs Móvil)
        if (usuario.getIdSesionActiva() != null && usuario.getPlataformaActiva() != null
                && !usuario.getPlataformaActiva().equalsIgnoreCase(plataforma)) {
            if (!request.debeForzarCierre()) {
                throw new BusinessConflictException(
                        "SESION_ACTIVA_PREVIA",
                        "Existe una sesión activa en la aplicación " + usuario.getPlataformaActiva()
                                + ". Confirme la finalización de dicha sesión para continuar."
                );
            }
            log.info("Expulsando sesión previa en {} para usuario {}", usuario.getPlataformaActiva(), correo);
        }

        // Registrar nueva sesión única y limpiar estado de verificación
        String nuevaSesionId = UUID.randomUUID().toString();
        usuario.setIdSesionActiva(nuevaSesionId);
        usuario.setPlataformaActiva(plataforma);
        usuario.setFechaInicioSesion(OffsetDateTime.now(ZoneOffset.UTC));
        usuario.setCodigo2fa(null);
        usuario.setExpiracion2fa(null);
        usuario.setIntentosFallidos(0);
        usuario.setFechaBloqueo(null);
        usuarioRepository.save(usuario);

        boolean recoveryOnly = !usuario.isActivo() || (usuario.getCliente() != null && !usuario.getCliente().isActivo());
        String token = jwtService.generarTokenConSesion(correo, recoveryOnly, nuevaSesionId, plataforma);

        log.info("Sesión única iniciada para {} en plataforma {}", correo, plataforma);
        return LoginResponse.exitoso(token, jwtService.getExpiracionEnSegundos(), recoveryOnly ? "RECOVERY" : "FULL");
    }

    @Override
    @Transactional(noRollbackFor = AccesoNoAutorizadoException.class)
    public LoginResponse reenviar2fa(Reenviar2faRequest request) {
        if (!jwtService.esToken2FA(request.ticketTemporal())) {
            throw new AccesoNoAutorizadoException("TICKET_INVALIDO", "El ticket de verificación 2FA no es válido.", 401);
        }

        String correo = jwtService.extraerCorreo(request.ticketTemporal());
        String plataforma = jwtService.extraerPlataforma(request.ticketTemporal());
        if (plataforma == null || plataforma.isBlank()) {
            plataforma = "MOVIL";
        }

        Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(correo)
                .orElseThrow(() -> new AccesoNoAutorizadoException("USUARIO_NO_ENCONTRADO", "Usuario no encontrado.", 401));

        verificarBloqueoTemporal(usuario);
        verificarCooldownReenvio(usuario);

        String nuevoOtp = generarCodigoOtp();
        usuario.setCodigo2fa(nuevoOtp);
        usuario.setExpiracion2fa(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));
        usuario.setIntentosFallidos(0);
        usuarioRepository.save(usuario);

        log.info("[2FA REENVÍO] Nuevo código OTP emitido para {}", correo);

        boolean correoEnviado = emailService.enviarCodigo2fa(usuario.getCorreo(), nuevoOtp, plataforma);
        if (!correoEnviado) {
            log.warn("No fue posible reenviar el correo 2FA a {}; no se expondrá el código OTP en la respuesta.", correo);
        }

        boolean sesionActivaPrevia = usuario.getIdSesionActiva() != null
                && usuario.getPlataformaActiva() != null
                && !usuario.getPlataformaActiva().equalsIgnoreCase(plataforma);

        String nuevoTicket = jwtService.generarTicket2FA(correo, plataforma);
        return LoginResponse.para2fa(
                nuevoTicket,
                300,
                sesionActivaPrevia,
                usuario.getPlataformaActiva(),
                null
        );
    }

    @Override
    @Transactional(noRollbackFor = AccesoNoAutorizadoException.class)
    public void solicitarRecuperacionPassword(SolicitarRecuperacionRequest request) {
        String correo = request.correo().trim();
        Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(correo)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "USUARIO_NO_ENCONTRADO",
                        "No encontramos una cuenta asociada a ese correo electrónico."
                ));

        verificarBloqueoTemporal(usuario);
        verificarCooldownReenvio(usuario);

        String codigoOtp = generarCodigoOtp();
        usuario.setCodigo2fa(codigoOtp);
        usuario.setExpiracion2fa(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));
        usuario.setIntentosFallidos(0);
        usuarioRepository.save(usuario);

        String nombre = usuario.getCliente() != null ? usuario.getCliente().getNombre() : "Cliente";
        log.info("[RECUPERACIÓN PASSWORD] Código OTP emitido para {}", usuario.getCorreo());

        boolean correoEnviado = emailService.enviarCodigoRecuperacionPassword(usuario.getCorreo(), nombre, codigoOtp);
        if (!correoEnviado) {
            log.warn("No fue posible enviar el correo de recuperación de contraseña a {}.", usuario.getCorreo());
        }
    }

    @Override
    @Transactional(noRollbackFor = AccesoNoAutorizadoException.class)
    public void restablecerPassword(RestablecerPasswordRequest request) {
        if (!PasswordPolicy.compatibleConBcrypt(request.passwordNueva())) {
            throw new ErrorValidacionException("La contraseña no puede superar 72 bytes en UTF-8 con BCrypt.");
        }

        Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(request.correo().trim())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "USUARIO_NO_ENCONTRADO",
                        "No encontramos una cuenta asociada a ese correo electrónico."
                ));

        validarCodigoOtpConBloqueo(usuario, request.codigoOtp());

        usuario.setPassword(passwordEncoder.encode(request.passwordNueva()));
        usuario.setCodigo2fa(null);
        usuario.setExpiracion2fa(null);
        usuario.setIntentosFallidos(0);
        usuario.setFechaBloqueo(null);
        usuario.setIdSesionActiva(null);
        usuario.setPlataformaActiva(null);
        usuarioRepository.save(usuario);

        log.info("Contraseña restablecida mediante código por correo para {}", usuario.getCorreo());
    }

    @Override
    @Transactional
    public void logout(String correoAutenticado) {
        if (correoAutenticado != null && !correoAutenticado.isBlank()) {
            usuarioRepository.findByCorreoIgnoreCase(correoAutenticado).ifPresent(u -> {
                u.setIdSesionActiva(null);
                u.setPlataformaActiva(null);
                usuarioRepository.save(u);
                log.info("Sesión cerrada exitosamente para usuario: {}", correoAutenticado);
            });
        }
    }

    private void verificarBloqueoTemporal(Usuario usuario) {
        if (usuario.getFechaBloqueo() != null) {
            if (usuario.getFechaBloqueo().plusMinutes(15).isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
                throw new AccesoNoAutorizadoException(
                        "CUENTA_BLOQUEADA",
                        "Su cuenta está bloqueada temporalmente por demasiados intentos fallidos. Intente más tarde.",
                        403
                );
            }
            usuario.setIntentosFallidos(0);
            usuario.setFechaBloqueo(null);
        }
    }

    private void verificarCooldownReenvio(Usuario usuario) {
        // Expiración total = 300s (5 min); impedir reenvíos dentro de los primeros 30s
        if (usuario.getExpiracion2fa() != null
                && usuario.getExpiracion2fa().minusSeconds(270).isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new AccesoNoAutorizadoException(
                    "REENVIO_DEMASIADO_PRONTO",
                    "Por favor espere 30 segundos antes de solicitar un nuevo código.",
                    429
            );
        }
    }

    private void validarCodigoOtpConBloqueo(Usuario usuario, String codigoIngresado) {
        verificarBloqueoTemporal(usuario);

        if (usuario.getExpiracion2fa() == null || usuario.getExpiracion2fa().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new AccesoNoAutorizadoException(
                    "CODIGO_EXPIRADO",
                    "El código de verificación ha expirado. Solicite uno nuevo.",
                    400
            );
        }

        if (usuario.getCodigo2fa() == null || !usuario.getCodigo2fa().equals(codigoIngresado.trim())) {
            int intentos = usuario.getIntentosFallidos() + 1;
            usuario.setIntentosFallidos(intentos);

            if (intentos >= 3) {
                usuario.setCodigo2fa(null);
                usuario.setExpiracion2fa(null);
                usuario.setFechaBloqueo(OffsetDateTime.now(ZoneOffset.UTC));
                usuarioRepository.save(usuario);
                throw new AccesoNoAutorizadoException(
                        "CODIGO_BLOQUEADO",
                        "Superó el límite de intentos de verificación. Por seguridad, solicite un nuevo acceso en 15 minutos.",
                        403
                );
            }

            usuarioRepository.save(usuario);
            int restantes = 3 - intentos;
            throw new AccesoNoAutorizadoException(
                    "CODIGO_INVALIDO",
                    "El código de verificación ingresado es incorrecto. Te quedan " + restantes + " intento(s).",
                    400
            );
        }
    }

    private String generarCodigoOtp() {
        int codigo = secureRandom.nextInt(900_000) + 100_000;
        return String.valueOf(codigo);
    }
}
