package com.proyecto.servicios.tasks;

import com.proyecto.servicios.service.GestoPagoTokenService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "gestopago.auth", name = "refresh-enabled", havingValue = "true", matchIfMissing = true)
public class GestoPagoTokenRefreshTask {

    private final GestoPagoTokenService tokenService;

    public GestoPagoTokenRefreshTask(GestoPagoTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Scheduled(
            fixedRateString = "${gestopago.auth.refresh-rate-ms:3600000}",
            initialDelayString = "${gestopago.auth.refresh-initial-delay-ms:0}"
    )
    public void renovarToken() {
        tokenService.renovarToken();
    }
}
