package com.proyecto.servicios.model.cliente;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {

    public static final String STRONG_PASSWORD_PATTERN =
            "^(?=.*\\p{Ll})(?=.*\\p{Lu})(?=.*\\d)(?=.*[\\p{P}\\p{S}]).+$";

    private PasswordPolicy() {
    }

    public static boolean compatibleConBcrypt(String password) {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    public static java.util.List<String> obtenerRequisitosFaltantes(String password) {
        java.util.List<String> faltantes = new java.util.ArrayList<>();
        if (password == null || password.length() < 8) {
            faltantes.add("al menos 8 caracteres");
        }
        if (password != null && password.getBytes(StandardCharsets.UTF_8).length > 72) {
            faltantes.add("máximo 72 caracteres");
        }
        if (password == null || !password.matches(".*\\p{Lu}.*")) {
            faltantes.add("al menos una letra mayúscula");
        }
        if (password == null || !password.matches(".*\\p{Ll}.*")) {
            faltantes.add("al menos una letra minúscula");
        }
        if (password == null || !password.matches(".*\\d.*")) {
            faltantes.add("al menos un número");
        }
        if (password == null || !password.matches(".*[\\p{P}\\p{S}].*")) {
            faltantes.add("al menos un carácter especial (ej. !@#$%)");
        }
        return faltantes;
    }
}
