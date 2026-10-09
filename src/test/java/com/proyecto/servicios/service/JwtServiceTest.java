package com.proyecto.servicios.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    @Test
    void tokenFirmadoSeValidaYExponeElCorreoComoSubject() {
        JwtService jwtService = new JwtService("0123456789abcdef0123456789abcdef", 60_000);
        String token = jwtService.generarToken("cliente@example.com");

        assertEquals("cliente@example.com", jwtService.extraerCorreo(token));
        assertTrue(jwtService.esValido(token, "cliente@example.com"));
        assertFalse(jwtService.esValido(token, "otro@example.com"));
        assertFalse(jwtService.esTokenDeRecuperacion(token));
    }

    @Test
    void tokenDeRecuperacionSeDistingueDelTokenNormal() {
        JwtService jwtService = new JwtService("0123456789abcdef0123456789abcdef", 60_000);
        String token = jwtService.generarToken("cliente@example.com", true);

        assertTrue(jwtService.esTokenDeRecuperacion(token));
    }
}
