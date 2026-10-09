package com.proyecto.servicios.service;

public final class ClabeUtils {

    private static final int[] PESOS = {3, 7, 1};

    private ClabeUtils() {
    }

    public static String agregarDigitoVerificador(String primerosDiecisieteDigitos) {
        if (primerosDiecisieteDigitos == null || !primerosDiecisieteDigitos.matches("\\d{17}")) {
            throw new IllegalArgumentException("La base de CLABE debe contener exactamente 17 dígitos.");
        }
        int suma = 0;
        for (int i = 0; i < primerosDiecisieteDigitos.length(); i++) {
            int producto = Character.digit(primerosDiecisieteDigitos.charAt(i), 10) * PESOS[i % PESOS.length];
            suma += producto % 10;
        }
        int digito = (10 - suma % 10) % 10;
        return primerosDiecisieteDigitos + digito;
    }

    public static boolean esValida(String clabe) {
        return clabe != null
                && clabe.matches("\\d{18}")
                && agregarDigitoVerificador(clabe.substring(0, 17)).equals(clabe);
    }
}
