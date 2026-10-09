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
    private static final String SCOPE_CLAIM = "scope";

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
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(correo)
                .claim(SCOPE_CLAIM, recoveryOnly ? RECOVERY_SCOPE : USER_SCOPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMillis)))
                .signWith(signingKey)
                .compact();
    }

    public String extraerCorreo(String token) {
        return extraerClaims(token).getSubject();
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
