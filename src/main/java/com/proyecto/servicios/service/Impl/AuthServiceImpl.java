package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.cliente.Usuario;
import com.proyecto.servicios.exception.AccesoNoAutorizadoException;
import com.proyecto.servicios.model.cliente.LoginRequest;
import com.proyecto.servicios.model.cliente.LoginResponse;
import com.proyecto.servicios.repositorys.cliente.UsuarioRepository;
import com.proyecto.servicios.service.AuthService;
import com.proyecto.servicios.service.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
@Service
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(UsuarioRepository usuarioRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new AccesoNoAutorizadoException(
                    "CREDENCIALES_INVALIDAS", "Correo electrónico o contraseña incorrectos.", 401
            );
        }
        
        Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(request.correo().trim())
                .orElseThrow(() -> new AccesoNoAutorizadoException(
                        "CREDENCIALES_INVALIDAS", "Correo electrónico o contraseña incorrectos.", 401
                ));

        // Verificar si la cuenta está bloqueada temporalmente (ej. 15 minutos)
        if (usuario.getFechaBloqueo() != null) {
            if (usuario.getFechaBloqueo().plusMinutes(15).isAfter(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC))) {
                throw new AccesoNoAutorizadoException(
                        "CUENTA_BLOQUEADA", "Su cuenta está bloqueada temporalmente por demasiados intentos fallidos. Intente más tarde.", 403
                );
            } else {
                // El tiempo de bloqueo ya pasó, se reinician los intentos
                usuario.setIntentosFallidos(0);
                usuario.setFechaBloqueo(null);
            }
        }

        if (!passwordEncoder.matches(request.password(), usuario.getPassword())) {
            int intentos = usuario.getIntentosFallidos() + 1;
            usuario.setIntentosFallidos(intentos);
            
            if (intentos >= 3) {
                usuario.setFechaBloqueo(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
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

        // Si el login es exitoso y había intentos fallidos previos, los reseteamos
        if (usuario.getIntentosFallidos() > 0) {
            usuario.setIntentosFallidos(0);
            usuario.setFechaBloqueo(null);
            usuarioRepository.save(usuario);
        }

        boolean recoveryOnly = !usuario.isActivo() || !usuario.getCliente().isActivo();
        return new LoginResponse(
                jwtService.generarToken(usuario.getCorreo(), recoveryOnly),
                "Bearer",
                jwtService.getExpiracionEnSegundos(),
                recoveryOnly ? "RECOVERY" : "FULL"
        );
    }
}
