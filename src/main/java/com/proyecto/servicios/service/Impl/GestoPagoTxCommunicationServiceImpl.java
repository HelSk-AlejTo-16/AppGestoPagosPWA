package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.client.GestoPagoTxClient;
import com.proyecto.servicios.exception.GestoPagoAuthException;
import com.proyecto.servicios.exception.GestoPagoCommunicationException;
import com.proyecto.servicios.exception.GestoPagoResponseException;
import com.proyecto.servicios.exception.GestoPagoTimeoutException;
import com.proyecto.servicios.model.gestopago.tx.GestoPagoTxResponse;
import com.proyecto.servicios.service.GestoPagoTokenService;
import com.proyecto.servicios.service.GestoPagoTxCommunicationService;
import feign.FeignException;
import feign.RetryableException;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class GestoPagoTxCommunicationServiceImpl implements GestoPagoTxCommunicationService {

    private static final DateTimeFormatter FORMATO_HORA_LOCAL = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final GestoPagoTxClient txClient;
    private final GestoPagoTokenService tokenService;
    private final ObjectMapper objectMapper;
    private final JAXBContext jaxbContext;

    @Value("${gestopago.auth.id-distribuidor}")
    private Integer idDistribuidor;

    @Value("${gestopago.auth.codigo-dispositivo}")
    private String codigoDispositivo;

    @Value("${gestopago.simulacion.enabled:false}")
    private boolean simulacionActiva;

    public GestoPagoTxCommunicationServiceImpl(GestoPagoTxClient txClient,
                                               GestoPagoTokenService tokenService,
                                               ObjectMapper objectMapper) {
        this.txClient = txClient;
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
        try {
            this.jaxbContext = JAXBContext.newInstance(GestoPagoTxResponse.class);
        } catch (JAXBException e) {
            throw new IllegalStateException("Error al inicializar JAXBContext para GestoPagoTxResponse", e);
        }
    }

    @Override
    public GestoPagoTxResponse ejecutarTransaccion(String idServicio,
                                                   String idProducto,
                                                   String referencia,
                                                   BigDecimal monto,
                                                   String upc) {
        String horaLocal = LocalDateTime.now().format(FORMATO_HORA_LOCAL);
        String montoPago = monto.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String refEnmascarada = enmascararReferencia(referencia);

        log.info("Procesando tx para pasarela GestoPago: idServicio={}, idProducto={}, ref={}, monto={}, upc={}",
                idServicio, idProducto, refEnmascarada, montoPago, upc);

        // Modo simulación académica: permite pruebas completas de autorización y reversas sin saldo en pasarela real
        if (simulacionActiva) {
            log.info("Modo simulación activo para GestoPago: procesando transacción UPC {}", upc);
            GestoPagoTxResponse sim = new GestoPagoTxResponse();
            sim.setFecha(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            sim.setHora(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));

            // Si la referencia termina en "0000", simula rechazo del carrier para verificar el reverso automático
            if (referencia != null && referencia.endsWith("0000")) {
                log.warn("Simulación: Disparador de rechazo activado por referencia terminada en 0000");
                sim.setCodigo("05");
                sim.setTexto("CARRIER TEMPORALMENTE NO DISPONIBLE");
                return sim;
            }

            // Si la referencia termina en "9999", simula rechazo por transacción duplicada en el carrier
            if (referencia != null && referencia.endsWith("9999")) {
                log.warn("Simulación: Disparador de duplicado activado por referencia terminada en 9999");
                sim.setCodigo("03");
                sim.setTexto("sendTx_duplicated: Transacción duplicada reportada por carrier");
                return sim;
            }

            // Aprobación exitosa por defecto
            sim.setCodigo("01");
            sim.setFolio("GP-SIM-" + System.currentTimeMillis());
            sim.setAutorizacion("AUTH-SIM-" + (100000 + (int) (Math.random() * 900000)));
            sim.setTexto("TRANSACCION APROBADA");
            return sim;
        }

        String bearerToken = obtenerBearerToken();

        try {
            String rawResponse = txClient.sendTransaction(
                    bearerToken,
                    idServicio,
                    idProducto,
                    referencia,
                    montoPago,
                    horaLocal,
                    upc
            );

            log.info("Respuesta cruda recibida de sendTx.do para UPC {}: {}", upc, rawResponse);
            return parsearRespuesta(rawResponse, upc);

        } catch (RetryableException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                log.error("Timeout de red al invocar sendTx.do para UPC {}: {}", upc, e.getMessage());
                throw new GestoPagoTimeoutException("Timeout al invocar pasarela GestoPago sendTx.do", e);
            }
            log.error("Error de reintento de conexión con sendTx.do para UPC {}: {}", upc, e.getMessage());
            throw new GestoPagoCommunicationException("Fallo de comunicación con pasarela GestoPago", e);

        } catch (FeignException.Unauthorized | FeignException.Forbidden e) {
            log.error("Autenticación rechazada por GestoPago sendTx.do (HTTP {}): {}", e.status(), e.getMessage());
            throw new GestoPagoAuthException("GestoPago rechazó la autenticación del token distribuidor", e);

        } catch (FeignException e) {
            log.error("GestoPago respondió con error HTTP {} para UPC {}: {}", e.status(), upc, e.getMessage());
            throw new GestoPagoCommunicationException("Error de pasarela GestoPago HTTP " + e.status(), e);
        }
    }

    private String obtenerBearerToken() {
        String token = tokenService.obtenerBearerToken(idDistribuidor, codigoDispositivo);
        if (token == null || token.isBlank()) {
            throw new GestoPagoAuthException("No se encontró token Bearer activo para GestoPago", null);
        }
        return token.startsWith("Bearer ") ? token : "Bearer " + token;
    }

    private GestoPagoTxResponse parsearRespuesta(String rawResponse, String upc) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new GestoPagoResponseException("Respuesta vacía de GestoPago para UPC " + upc, null);
        }

        String limpio = rawResponse.trim();

        // Si es XML
        if (limpio.startsWith("<")) {
            try {
                Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
                return (GestoPagoTxResponse) unmarshaller.unmarshal(new StringReader(limpio));
            } catch (JAXBException e) {
                log.warn("Fallo al parsear XML de sendTx.do, intentando extraer información: {}", e.getMessage());
            }
        }

        // Si es JSON de error (ej. {"success":false, "token":"INVALID", "message":"..."})
        if (limpio.startsWith("{")) {
            try {
                JsonNode root = objectMapper.readTree(limpio);
                GestoPagoTxResponse fallback = new GestoPagoTxResponse();
                fallback.setCodigo(root.has("codigo") ? root.get("codigo").asText() : "99");
                fallback.setTexto(root.has("message") ? root.get("message").asText() : "Error devuelto en formato JSON");
                return fallback;
            } catch (Exception jsonEx) {
                log.warn("Fallo al parsear JSON devuelto por GestoPago: {}", jsonEx.getMessage());
            }
        }

        // Fallback genérico de rechazo
        GestoPagoTxResponse gen = new GestoPagoTxResponse();
        gen.setCodigo("99");
        gen.setTexto("Respuesta de proveedor no interpretable: " + (limpio.length() > 100 ? limpio.substring(0, 100) : limpio));
        return gen;
    }

    private String enmascararReferencia(String ref) {
        if (ref == null) return "null";
        int len = ref.length();
        if (len <= 4) return "****";
        return ref.substring(0, 2) + "*".repeat(len - 4) + ref.substring(len - 2);
    }
}
