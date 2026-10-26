package com.proyecto.servicios.util;

import com.proyecto.servicios.exception.ErrorValidacionException;
import com.proyecto.servicios.service.ClabeUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ReferenciaValidatorTest {

    @Test
    @DisplayName("Referencia nula o vacía debe lanzar ErrorValidacionException")
    void testReferenciaVacia() {
        assertThrows(ErrorValidacionException.class, () ->
                ReferenciaValidator.validarReferencia(null, 1, false));
        assertThrows(ErrorValidacionException.class, () ->
                ReferenciaValidator.validarReferencia("   ", 1, false));
    }

    // ==========================================
    // tipoFront = 1 (Telefonía / Tiempo Aire)
    // ==========================================

    @Test
    @DisplayName("tipoFront 1: Teléfono válido de 10 dígitos debe pasar")
    void testTelefoniaValida() {
        assertDoesNotThrow(() ->
                ReferenciaValidator.validarReferencia("5512345678", 1, false));
        assertDoesNotThrow(() ->
                ReferenciaValidator.validarReferencia("8183456789", 1, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456789", "12345678901", "551234567A", "55-123-456", "+5255123456"})
    @DisplayName("tipoFront 1: Longitudes distintas a 10 o caracteres no numéricos deben fallar")
    void testTelefoniaInvalida(String ref) {
        ErrorValidacionException ex = assertThrows(ErrorValidacionException.class, () ->
                ReferenciaValidator.validarReferencia(ref, 1, false));
        assertTrue(ex.getMessage().contains("10 dígitos"));
    }

    // ==========================================
    // tipoFront = 2 (Servicios Públicos)
    // ==========================================

    @Test
    @DisplayName("tipoFront 2: Servicio público válido sin dígito verificador")
    void testServicioSinDigitoVerificador() {
        assertDoesNotThrow(() ->
                ReferenciaValidator.validarReferencia("CFE123456789", 2, false));
    }

    @Test
    @DisplayName("tipoFront 2: Con hasDigitoVerificador = true y Luhn válido debe pasar")
    void testServicioConDigitoVerificadorValido() {
        // "79927398713" es un número válido según el algoritmo de Luhn
        assertDoesNotThrow(() ->
                ReferenciaValidator.validarReferencia("79927398713", 2, true));
    }

    @Test
    @DisplayName("tipoFront 2: Con hasDigitoVerificador = true y Luhn inválido debe fallar")
    void testServicioConDigitoVerificadorInvalido() {
        // "79927398710" tiene dígito verificador erróneo
        ErrorValidacionException ex = assertThrows(ErrorValidacionException.class, () ->
                ReferenciaValidator.validarReferencia("79927398710", 2, true));
        assertTrue(ex.getMessage().contains("dígito verificador"));
    }

    // ==========================================
    // tipoFront = 40 (Transferencias / Remesas)
    // ==========================================

    @Test
    @DisplayName("tipoFront 40: Tarjeta de 16 dígitos válida por Luhn")
    void testTransferenciaTarjetaValida() {
        // 49927398716 es un ejemplo válido de 11 dígitos, expandido a 16 dígitos con Luhn válido:
        // 4532015112830366 es una tarjeta Visa válida de prueba
        assertTrue(ReferenciaValidator.esLuhnValido("4532015112830366"));
        assertDoesNotThrow(() ->
                ReferenciaValidator.validarReferencia("4532015112830366", 40, false));
    }

    @Test
    @DisplayName("tipoFront 40: Tarjeta de 16 dígitos con Luhn inválido debe fallar")
    void testTransferenciaTarjetaInvalida() {
        assertThrows(ErrorValidacionException.class, () ->
                ReferenciaValidator.validarReferencia("4532015112830367", 40, false));
    }

    @Test
    @DisplayName("tipoFront 40: CLABE de 18 dígitos válida según ClabeUtils")
    void testTransferenciaClabeValida() {
        String clabeValida = ClabeUtils.agregarDigitoVerificador("00218001234567890");
        assertEquals(18, clabeValida.length());
        assertDoesNotThrow(() ->
                ReferenciaValidator.validarReferencia(clabeValida, 40, false));
    }

    @Test
    @DisplayName("tipoFront 40: Longitud que no es ni 16 ni 18 dígitos debe fallar")
    void testTransferenciaLongitudInvalida() {
        assertThrows(ErrorValidacionException.class, () ->
                ReferenciaValidator.validarReferencia("123456789012345", 40, false)); // 15 dígitos
    }
}

