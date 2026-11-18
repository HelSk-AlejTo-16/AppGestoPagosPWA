package com.proyecto.servicios.model.cliente;

public record LoginResponse(
        String token,
        String tipo,
        long expiraEnSegundos,
        String modoAcceso,
        boolean requiere2fa,
        boolean sesionActivaPrevia,
        String plataformaPrevia,
        String mensajeSesion,
        String codigoOtpSimulado
) {
    public static LoginResponse para2fa(
            String ticketTemporal,
            long expiraEnSegundos,
            boolean sesionActivaPrevia,
            String plataformaPrevia,
            String codigoOtp
    ) {
        String mensaje = sesionActivaPrevia
                ? "Se requiere autenticación 2FA. Existe una sesión activa en " + plataformaPrevia + "."
                : "Se requiere autenticación de doble factor (2FA).";
        return new LoginResponse(
                ticketTemporal,
                "2FA_TICKET",
                expiraEnSegundos,
                "2FA_PENDING",
                true,
                sesionActivaPrevia,
                plataformaPrevia,
                mensaje,
                codigoOtp
        );
    }

    public static LoginResponse exitoso(String token, long expiraEnSegundos, String modoAcceso) {
        return new LoginResponse(
                token,
                "Bearer",
                expiraEnSegundos,
                modoAcceso,
                false,
                false,
                null,
                "Sesión iniciada exitosamente.",
                null
        );
    }
}
