package com.proyecto.servicios.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    public static final String RECOVERY_SCOPE = "RECOVERY";
    public static final String USER_SCOPE = "USER";
    public static final String TWO_FACTOR_SCOPE = "2FA_TICKET";
    private static final String SCOPE_CLAIM = "scope";
    private static final String SESSION_CLAIM = "sid";
    private static final String PLATFORM_CLAIM = "plat";

    private final SecretKey signingKey;
    private final long expirationMillis;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-ms:3600000}") long expirationMillis) {
        byte[] keyBytes = secret.startsWith("base64:")
                ? Decoders.BASE64.decode(secret.substring("base64:".length()))
                : secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET debe tener al menos 32 bytes.");
        }
        if (expirationMillis <= 0) {
            throw new IllegalArgumentException("La expiración JWT debe ser positiva.");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMillis = expirationMillis;
    }

    public String generarToken(String correo) {
        return generarToken(correo, false);
    }

    public String generarToken(String correo, boolean recoveryOnly) {
        return generarTokenConSesion(correo, recoveryOnly, null, null);
    }

    public String generarTicket2FA(String correo, String plataforma) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(correo)
                .claim(SCOPE_CLAIM, TWO_FACTOR_SCOPE)
                .claim(PLATFORM_CLAIM, plataforma != null ? plataforma : "MOVIL")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(300000))) // 5 minutos de vigencia
                .signWith(signingKey)
                .compact();
    }

    public String generarTokenConSesion(String correo, boolean recoveryOnly, String idSesion, String plataforma) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(correo)
                .claim(SCOPE_CLAIM, recoveryOnly ? RECOVERY_SCOPE : USER_SCOPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMillis)));

        if (idSesion != null) {
            builder.claim(SESSION_CLAIM, idSesion);
        }
        if (plataforma != null) {
            builder.claim(PLATFORM_CLAIM, plataforma);
        }

        return builder.signWith(signingKey).compact();
    }

    public String extraerCorreo(String token) {
        return extraerClaims(token).getSubject();
    }

    public String extraerSessionId(String token) {
        return extraerClaims(token).get(SESSION_CLAIM, String.class);
    }

    public String extraerPlataforma(String token) {
        return extraerClaims(token).get(PLATFORM_CLAIM, String.class);
    }

    public boolean esToken2FA(String token) {
        return TWO_FACTOR_SCOPE.equals(extraerClaims(token).get(SCOPE_CLAIM, String.class));
    }

    public boolean esValido(String token, String correo) {
        Claims claims = extraerClaims(token);
        return correo.equalsIgnoreCase(claims.getSubject())
                && claims.getExpiration().after(new Date());
    }

    public boolean esTokenDeRecuperacion(String token) {
        return RECOVERY_SCOPE.equals(extraerClaims(token).get(SCOPE_CLAIM, String.class));
    }

    public long getExpiracionEnSegundos() {
        return expirationMillis / 1000;
    }

    private Claims extraerClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
