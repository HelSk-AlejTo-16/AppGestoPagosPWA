package com.proyecto.servicios.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UpcGeneratorTest {

    @Test
    @DisplayName("Debe generar un UPC con longitud exacta de 10 caracteres")
    void testLongitudUpc() {
        String upc = UpcGenerator.generarUpc();
        assertNotNull(upc);
        assertEquals(10, upc.length());
    }

    @Test
    @DisplayName("Debe contener únicamente caracteres alfanuméricos en mayúsculas [0-9A-Z]")
    void testFormatoAlfanumerico() {
        for (int i = 0; i < 50; i++) {
            String upc = UpcGenerator.generarUpc();
            assertTrue(upc.matches("^[0-9A-Z]{10}$"), "El UPC generado no cumple el patrón alfanumérico: " + upc);
        }
    }

    @Test
    @DisplayName("Debe generar valores únicos con alta entropía sin colisiones")
    void testUnicidadUpc() {
        int total = 1000;
        Set<String> generados = new HashSet<>(total);
        for (int i = 0; i < total; i++) {
            String upc = UpcGenerator.generarUpc();
            assertTrue(generados.add(upc), "Colisión detectada para UPC: " + upc);
        }
        assertEquals(total, generados.size());
    }
}

