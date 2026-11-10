package com.proyecto.servicios.model.cliente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record Verificar2faRequest(
        @NotBlank(message = "El ticket temporal de 2FA es obligatorio.")
        String ticketTemporal,

        @NotBlank(message = "El código de verificación (OTP) es obligatorio.")
        @Pattern(regexp = "^\\d{6}$", message = "El código OTP debe ser exactamente de 6 dígitos numéricos.")
        String codigoOtp,

        Boolean forzarCierreSesion
) {
    public boolean debeForzarCierre() {
        return forzarCierreSesion != null && forzarCierreSesion;
    }
}
