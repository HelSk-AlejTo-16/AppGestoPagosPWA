package com.proyecto.servicios.util;

import java.security.SecureRandom;

public final class UpcGenerator {

    private static final String ALFANUMERICO = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();
    public static final int LONGITUD_UPC = 10;

    private UpcGenerator() {
    }

    /**
     * Genera un identificador único (UPC) de 10 caracteres alfanuméricos en mayúsculas
     * con alta entropía mediante SecureRandom para actuar como Idempotency Key.
     */
    public static String generarUpc() {
        char[] buffer = new char[LONGITUD_UPC];
        for (int i = 0; i < LONGITUD_UPC; i++) {
            buffer[i] = ALFANUMERICO.charAt(RANDOM.nextInt(ALFANUMERICO.length()));
        }
        return new String(buffer);
    }
}

