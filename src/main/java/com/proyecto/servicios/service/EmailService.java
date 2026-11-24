package com.proyecto.servicios.service;

import com.proyecto.servicios.util.InputSanitizer;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${BREVO_API_KEY:}")
    private String brevoApiKey;

    @Value("${MAIL_FROM:}")
    private String mailFrom;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    public boolean enviarCodigo2fa(String destinatario, String codigoOtp, String plataforma) {
        String asunto = "Código de verificación 2FA: " + codigoOtp + " - Banca Digital";
        String html = construirPlantillaHtml2fa(codigoOtp, plataforma);
        return enviarCorreoHtml(destinatario, asunto, html, "2FA (plataforma=" + plataforma + ")");
    }

    public boolean enviarCodigoConfirmacionCuenta(String destinatario, String nombre, String codigoOtp) {
        String asunto = "Confirma tu nueva cuenta bancaria: " + codigoOtp + " - Banca Digital";
        String html = construirPlantillaHtmlConfirmacionCuenta(nombre, codigoOtp);
        return enviarCorreoHtml(destinatario, asunto, html, "confirmación de cuenta");
    }

    public boolean enviarCodigoRecuperacionPassword(String destinatario, String nombre, String codigoOtp) {
        String asunto = "Restablecer contraseña: " + codigoOtp + " - Banca Digital";
        String html = construirPlantillaHtmlRecuperacion(nombre, codigoOtp);
        return enviarCorreoHtml(destinatario, asunto, html, "recuperación de contraseña");
    }

    private boolean enviarCorreoHtml(String destinatario, String asunto, String contenidoHtml, String contextoLog) {
        String destinatarioSeguro = InputSanitizer.sanitizarTexto(destinatario);
        String asuntoSeguro = InputSanitizer.sanitizarTexto(asunto);
        if (destinatarioSeguro == null || destinatarioSeguro.isBlank()) {
            return false;
        }

        // Prioridad 1: API HTTP de Brevo (puerto 443 HTTPS, ideal para Render Free donde SMTP 587 está bloqueado)
        if (brevoApiKey != null && !brevoApiKey.isBlank()) {
            String remitente = (mailFrom != null && !mailFrom.isBlank()) ? mailFrom.trim() : mailUsername;
            if (remitente != null && !remitente.isBlank()) {
                if (enviarPorBrevoApi(remitente, destinatarioSeguro, asuntoSeguro, contenidoHtml, contextoLog)) {
                    return true;
                }
            } else {
                log.warn("BREVO_API_KEY configurada pero falta MAIL_FROM o MAIL_USERNAME como correo remitente.");
            }
        }

        // Prioridad 2: SMTP estándar (Gmail / Brevo puerto 2525 / Mailjet)
        if (mailSender == null || mailUsername == null || mailUsername.isBlank()
                || mailPassword == null || mailPassword.isBlank()) {
            log.warn("Credenciales de correo no configuradas (BREVO_API_KEY o MAIL_USERNAME / MAIL_PASSWORD). "
                    + "Se omite el envío real de correo de {} para {}.",
                    contextoLog, InputSanitizer.sanitizarParaLog(destinatarioSeguro));
            return false;
        }

        try {
            String remitente = (mailFrom != null && !mailFrom.isBlank()) ? mailFrom.trim() : mailUsername;
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(remitente, "Banca Digital GestoPago");
            helper.setTo(destinatarioSeguro);
            helper.setSubject(asuntoSeguro);
            helper.setText(contenidoHtml, true);

            mailSender.send(message);
            log.info("Correo SMTP de {} enviado exitosamente a {}",
                    contextoLog, InputSanitizer.sanitizarParaLog(destinatarioSeguro));
            return true;
        } catch (Exception e) {
            log.warn("No se pudo enviar el correo SMTP de {} a {} ({}): {}",
                    contextoLog, InputSanitizer.sanitizarParaLog(destinatarioSeguro),
                    e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    private boolean enviarPorBrevoApi(String remitente, String destinatario, String asunto,
                                      String contenidoHtml, String contextoLog) {
        try {
            JSONObject payload = new JSONObject()
                    .put("sender", new JSONObject()
                            .put("name", "Banca Digital GestoPago")
                            .put("email", remitente))
                    .put("to", new JSONArray().put(new JSONObject().put("email", destinatario)))
                    .put("subject", asunto)
                    .put("htmlContent", contenidoHtml);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                    .timeout(Duration.ofSeconds(10))
                    .header("accept", "application/json")
                    .header("content-type", "application/json")
                    .header("api-key", brevoApiKey.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Correo Brevo HTTPS de {} enviado exitosamente a {}", contextoLog, destinatario);
                return true;
            }
            String cuerpo = response.body() == null ? "" : response.body();
            log.warn("Brevo API respondió HTTP {} al enviar correo de {} a {}. Detalle: {}",
                    response.statusCode(), contextoLog, destinatario,
                    InputSanitizer.sanitizarParaLog(cuerpo));
            return false;
        } catch (Exception e) {
            log.warn("Fallo al enviar correo por Brevo API de {} a {} ({}): {}",
                    contextoLog, destinatario, e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    private String construirPlantillaHtml2fa(String codigoOtp, String plataforma) {
        String plat = InputSanitizer.escaparHtml(
                (plataforma == null || plataforma.isBlank()) ? "APLICACIÓN" : plataforma.trim()
        );
        String otpSeguro = InputSanitizer.escaparHtml(codigoOtp);
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
                """.formatted(plat, otpSeguro);
    }

    private String construirPlantillaHtmlConfirmacionCuenta(String nombre, String codigoOtp) {
        String titular = InputSanitizer.escaparHtml(
                (nombre == null || nombre.isBlank()) ? "Cliente" : nombre.trim()
        );
        String otpSeguro = InputSanitizer.escaparHtml(codigoOtp);
        return """
                <div style="font-family: Arial, sans-serif; background-color: #f4f7fb; padding: 24px;">
                  <div style="max-width: 520px; margin: 0 auto; background: #ffffff; border-radius: 12px; padding: 28px; border: 1px solid #e3e8ee;">
                    <h2 style="color: #0a2540; margin-top: 0;">Confirma tu Cuenta Bancaria</h2>
                    <p style="color: #425466; font-size: 14px; line-height: 1.5;">
                      Hola <strong>%s</strong>, gracias por registrarte en <strong>Banca Digital GestoPago</strong>.
                      Ingresa la siguiente clave en la pantalla de confirmación para activar tu cuenta:
                    </p>
                    <div style="margin: 22px 0; padding: 16px; background: #f0f5ff; border: 1px dashed #0066ff; border-radius: 10px; text-align: center;">
                      <span style="display: block; font-size: 12px; color: #697386; margin-bottom: 6px;">CLAVE DE CONFIRMACIÓN</span>
                      <span style="font-size: 32px; font-weight: bold; letter-spacing: 6px; color: #0a2540;">%s</span>
                    </div>
                    <p style="color: #425466; font-size: 13px; line-height: 1.5;">
                      Esta clave es de un solo uso y tiene una vigencia de <strong>5 minutos</strong>.
                    </p>
                    <p style="color: #8792a2; font-size: 12px; margin-bottom: 0;">
                      Si no creaste esta cuenta, puedes ignorar este mensaje de forma segura.
                    </p>
                  </div>
                </div>
                """.formatted(titular, otpSeguro);
    }

    private String construirPlantillaHtmlRecuperacion(String nombre, String codigoOtp) {
        String titular = InputSanitizer.escaparHtml(
                (nombre == null || nombre.isBlank()) ? "Cliente" : nombre.trim()
        );
        String otpSeguro = InputSanitizer.escaparHtml(codigoOtp);
        return """
                <div style="font-family: Arial, sans-serif; background-color: #f4f7fb; padding: 24px;">
                  <div style="max-width: 520px; margin: 0 auto; background: #ffffff; border-radius: 12px; padding: 28px; border: 1px solid #e3e8ee;">
                    <h2 style="color: #0a2540; margin-top: 0;">Recuperación de Contraseña</h2>
                    <p style="color: #425466; font-size: 14px; line-height: 1.5;">
                      Hola <strong>%s</strong>, recibimos una solicitud para restablecer la contraseña de tu cuenta en
                      <strong>Banca Digital GestoPago</strong>.
                    </p>
                    <div style="margin: 22px 0; padding: 16px; background: #f0f5ff; border: 1px dashed #0066ff; border-radius: 10px; text-align: center;">
                      <span style="display: block; font-size: 12px; color: #697386; margin-bottom: 6px;">CÓDIGO DE RECUPERACIÓN</span>
                      <span style="font-size: 32px; font-weight: bold; letter-spacing: 6px; color: #0a2540;">%s</span>
                    </div>
                    <p style="color: #425466; font-size: 13px; line-height: 1.5;">
                      Este código es de un solo uso y tiene una vigencia de <strong>5 minutos</strong>.
                    </p>
                    <p style="color: #8792a2; font-size: 12px; margin-bottom: 0;">
                      Si no solicitaste el cambio de contraseña, ignora este correo y protege tu acceso.
                    </p>
                  </div>
                </div>
                """.formatted(titular, otpSeguro);
    }
}
