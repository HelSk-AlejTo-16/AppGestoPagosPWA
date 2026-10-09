package com.proyecto.servicios.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationService {

    public void alertFallbackTriggered(String fallbackName, String reason) {
        // En el futuro, aquí puedes agregar la lógica para enviar un email
        // usando spring-boot-starter-mail, o un webhook de Slack/Discord.
        log.warn("==================================================");
        log.warn("🚨 ALERTA DE FALLBACK ACTIVADA 🚨");
        log.warn("El sistema ha activado el respaldo: {}", fallbackName);
        log.warn("Razón: {}", reason);
        log.warn("==================================================");
    }
}
