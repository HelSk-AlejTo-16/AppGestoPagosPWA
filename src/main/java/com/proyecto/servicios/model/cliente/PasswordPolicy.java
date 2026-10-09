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
}
