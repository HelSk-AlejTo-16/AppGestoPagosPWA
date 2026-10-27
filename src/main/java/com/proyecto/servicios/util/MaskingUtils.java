package com.proyecto.servicios.util;

/**
 * Utilitario de seguridad para enmascarar información financiera y personal sensible
 * (números de cuenta, teléfonos, referencias de contrato) antes de escribir en logs de auditoría.
 */
public final class MaskingUtils {

    private MaskingUtils() {
    }

    /**
     * Enmascara un número de cuenta bancaria dejando visibles únicamente los últimos 4 dígitos.
     * Ejemplo: "12345678901234567890" -> "****************7890"
     */
    public static String enmascararCuenta(String numeroCuenta) {
        if (numeroCuenta == null || numeroCuenta.isBlank()) {
            return "****";
        }
        String limpio = numeroCuenta.trim();
        int len = limpio.length();
        if (len <= 4) {
            return "****";
        }
        return "*".repeat(len - 4) + limpio.substring(len - 4);
    }

    /**
     * Enmascara una referencia de servicio o número telefónico dejando visibles solo los primeros 2 y últimos 2 caracteres.
     * Ejemplo: "5512345678" -> "55******78"
     */
    public static String enmascararReferencia(String referencia) {
        if (referencia == null || referencia.isBlank()) {
            return "****";
        }
        String limpio = referencia.trim();
        int len = limpio.length();
        if (len <= 4) {
            return "****";
        }
        return limpio.substring(0, 2) + "*".repeat(len - 4) + limpio.substring(len - 2);
    }
}

