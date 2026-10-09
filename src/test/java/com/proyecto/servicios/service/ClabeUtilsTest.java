package com.proyecto.servicios.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClabeUtilsTest {

    @Test
    void reconoceClabeConDigitoVerificadorValido() {
        assertTrue(ClabeUtils.esValida("032180000118359719"));
    }

    @Test
    void rechazaClabeConDigitoVerificadorIncorrecto() {
        assertFalse(ClabeUtils.esValida("032180000118359718"));
    }

    @Test
    void calculaDigitoVerificador() {
        assertEquals("032180000118359719", ClabeUtils.agregarDigitoVerificador("03218000011835971"));
    }
}
