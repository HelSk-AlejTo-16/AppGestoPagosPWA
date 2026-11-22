package com.proyecto.servicios.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    public boolean enviarCodigo2fa(String destinatario, String codigoOtp, String plataforma) {
        if (mailSender == null || mailUsername == null || mailUsername.isBlank()
                || mailPassword == null || mailPassword.isBlank()) {
            log.warn("Credenciales SMTP no configuradas (MAIL_USERNAME / MAIL_PASSWORD). "
                    + "Se omite el envío real de correo 2FA para {}.", destinatario);
            return false;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailUsername, "Banca Digital GestoPago");
            helper.setTo(destinatario);
            helper.setSubject("Código de verificación 2FA: " + codigoOtp + " - Banca Digital");
            helper.setText(construirPlantillaHtml2fa(codigoOtp, plataforma), true);

            mailSender.send(message);
            log.info("Correo 2FA enviado exitosamente a {} (plataforma={})", destinatario, plataforma);
            return true;
        } catch (Exception e) {
            log.warn("No se pudo enviar el correo 2FA a {} ({}): {}",
                    destinatario, e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    private String construirPlantillaHtml2fa(String codigoOtp, String plataforma) {
        String plat = (plataforma == null || plataforma.isBlank()) ? "APLICACIÓN" : plataforma;
        return """
                <div style="font-family: Arial, sans-serif; background-color: #f4f7fb; padding: 24px;">
                  <div style="max-width: 520px; margin: 0 auto; background: #ffffff; border-radius: 12px; padding: 28px; border: 1px solid #e3e8ee;">
                    <h2 style="color: #0a2540; margin-top: 0;">Verificación de Seguridad 2FA</h2>
                    <p style="color: #425466; font-size: 14px; line-height: 1.5;">
                      Recibimos una solicitud de inicio de sesión en <strong>Banca Digital GestoPago</strong> desde la plataforma:
                      <strong>%s</strong>.
                    </p>
                    <div style="margin: 22px 0; padding: 16px; background: #f0f5ff; border: 1px dashed #0066ff; border-radius: 10px; text-align: center;">
                      <span style="display: block; font-size: 12px; color: #697386; margin-bottom: 6px;">TU CÓDIGO DE VERIFICACIÓN</span>
                      <span style="font-size: 32px; font-weight: bold; letter-spacing: 6px; color: #0a2540;">%s</span>
                    </div>
                    <p style="color: #425466; font-size: 13px; line-height: 1.5;">
                      Este código es de un solo uso y tiene una vigencia de <strong>5 minutos</strong>.
                    </p>
                    <p style="color: #8792a2; font-size: 12px; margin-bottom: 0;">
                      Si no solicitaste este acceso, ignora este correo y cambia tu contraseña inmediatamente.
                    </p>
                  </div>
                </div>
                """.formatted(plat, codigoOtp);
    }
}
