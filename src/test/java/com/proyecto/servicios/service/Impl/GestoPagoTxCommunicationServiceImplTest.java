package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.client.GestoPagoTxClient;
import com.proyecto.servicios.exception.GestoPagoAuthException;
import com.proyecto.servicios.exception.GestoPagoTimeoutException;
import com.proyecto.servicios.model.gestopago.tx.GestoPagoTxResponse;
import com.proyecto.servicios.service.GestoPagoTokenService;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.RetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GestoPagoTxCommunicationServiceImplTest {

    @Mock
    private GestoPagoTxClient txClient;

    @Mock
    private GestoPagoTokenService tokenService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private GestoPagoTxCommunicationServiceImpl communicationService;

    @BeforeEach
    void setUp() {
        communicationService = new GestoPagoTxCommunicationServiceImpl(txClient, tokenService, objectMapper);
        ReflectionTestUtils.setField(communicationService, "idDistribuidor", 83);
        ReflectionTestUtils.setField(communicationService, "codigoDispositivo", "GPS83-TPV-17");
    }

    @Test
    @DisplayName("Debe parsear exitosamente una respuesta XML con código 01 y folios")
    void testRespuestaExitosaXml() {
        when(tokenService.obtenerBearerToken(anyInt(), anyString())).thenReturn("mock-token");
        String xml = """
                <RESPONSE>
                    <CODIGO>01</CODIGO>
                    <FOLIO>88776655</FOLIO>
                    <AUTORIZACION>AUT-2026-X</AUTORIZACION>
                    <TEXTO>TRANSACCION EXITOSA</TEXTO>
                    <FECHA>20261020</FECHA>
                    <HORA>013000</HORA>
                </RESPONSE>
                """;
        when(txClient.sendTransaction(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(xml);

        GestoPagoTxResponse response = communicationService.ejecutarTransaccion(
                "1", "11", "5512345678", new BigDecimal("100.00"), "UPC1234567"
        );

        assertNotNull(response);
        assertTrue(response.isAprobada());
        assertEquals("01", response.getCodigo());
        assertEquals("88776655", response.getFolio());
        assertEquals("AUT-2026-X", response.getAutorizacion());
        assertEquals("TRANSACCION EXITOSA", response.getTexto());
    }

    @Test
    @DisplayName("Debe interpretar correctamente un XML con código de rechazo del proveedor")
    void testRespuestaRechazoXml() {
        when(tokenService.obtenerBearerToken(anyInt(), anyString())).thenReturn("mock-token");
        String xml = """
                <RESPONSE>
                    <CODIGO>05</CODIGO>
                    <TEXTO>CARRIER TELCEL TEMPORALMENTE FUERA DE SERVICIO</TEXTO>
                </RESPONSE>
                """;
        when(txClient.sendTransaction(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(xml);

        GestoPagoTxResponse response = communicationService.ejecutarTransaccion(
                "1", "11", "5512345678", new BigDecimal("100.00"), "UPC1234567"
        );

        assertNotNull(response);
        assertFalse(response.isAprobada());
        assertEquals("05", response.getCodigo());
        assertTrue(response.getTexto().contains("CARRIER TELCEL"));
    }

    @Test
    @DisplayName("Debe manejar respuestas de error en formato JSON sin fallar por JAXB")
    void testRespuestaJsonError() {
        when(tokenService.obtenerBearerToken(anyInt(), anyString())).thenReturn("mock-token");
        String json = "{\"success\":false, \"message\":\"Saldo del distribuidor insuficiente\"}";
        when(txClient.sendTransaction(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(json);

        GestoPagoTxResponse response = communicationService.ejecutarTransaccion(
                "1", "11", "5512345678", new BigDecimal("100.00"), "UPC1234567"
        );

        assertNotNull(response);
        assertFalse(response.isAprobada());
        assertEquals("99", response.getCodigo());
        assertTrue(response.getTexto().contains("Saldo del distribuidor insuficiente"));
    }

    @Test
    @DisplayName("Debe lanzar GestoPagoTimeoutException cuando Feign captura un SocketTimeoutException")
    void testTimeoutException() {
        when(tokenService.obtenerBearerToken(anyInt(), anyString())).thenReturn("mock-token");
        Request request = Request.create(Request.HttpMethod.POST, "/sistema/service/sendTx.do",
                Collections.emptyMap(), null, new RequestTemplate());
        RetryableException timeout = new RetryableException(
                504, "Read timed out", Request.HttpMethod.POST, new SocketTimeoutException("Read timed out"), new Date(), request
        );
        when(txClient.sendTransaction(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(timeout);

        assertThrows(GestoPagoTimeoutException.class, () ->
                communicationService.ejecutarTransaccion("1", "11", "5512345678", new BigDecimal("100.00"), "UPC1234567")
        );
    }

    @Test
    @DisplayName("Debe lanzar GestoPagoAuthException si el token es nulo o vacío")
    void testTokenFaltante() {
        when(tokenService.obtenerBearerToken(anyInt(), anyString())).thenReturn(null);

        assertThrows(GestoPagoAuthException.class, () ->
                communicationService.ejecutarTransaccion("1", "11", "5512345678", new BigDecimal("100.00"), "UPC1234567")
        );
    }

    @Test
    @DisplayName("Modo simulación: si está activo debe devolver respuesta aprobada sin invocar Feign")
    void testModoSimulacion_Aprobado() {
        ReflectionTestUtils.setField(communicationService, "simulacionActiva", true);

        GestoPagoTxResponse response = communicationService.ejecutarTransaccion(
                "133", "406", "5512345678", new BigDecimal("100.00"), "UPC-SIM-01"
        );

        assertNotNull(response);
        assertTrue(response.isAprobada());
        assertEquals("01", response.getCodigo());
        assertTrue(response.getFolio().startsWith("GP-SIM-"));
        assertTrue(response.getAutorizacion().startsWith("AUTH-SIM-"));
        verify(txClient, never()).sendTransaction(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Modo simulación: referencia terminada en 0000 debe simular rechazo para pruebas de reversa")
    void testModoSimulacion_Rechazo() {
        ReflectionTestUtils.setField(communicationService, "simulacionActiva", true);

        GestoPagoTxResponse response = communicationService.ejecutarTransaccion(
                "133", "406", "5512340000", new BigDecimal("100.00"), "UPC-SIM-02"
        );

        assertNotNull(response);
        assertFalse(response.isAprobada());
        assertEquals("05", response.getCodigo());
        assertEquals("CARRIER TEMPORALMENTE NO DISPONIBLE", response.getTexto());
        verify(txClient, never()).sendTransaction(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Modo simulación: referencia terminada en 9999 debe simular sendTx_duplicated")
    void testModoSimulacion_Duplicado() {
        ReflectionTestUtils.setField(communicationService, "simulacionActiva", true);

        GestoPagoTxResponse response = communicationService.ejecutarTransaccion(
                "133", "406", "5512349999", new BigDecimal("100.00"), "UPC-SIM-03"
        );

        assertNotNull(response);
        assertFalse(response.isAprobada());
        assertEquals("03", response.getCodigo());
        assertTrue(response.getTexto().contains("sendTx_duplicated"));
        verify(txClient, never()).sendTransaction(any(), any(), any(), any(), any(), any(), any());
    }
}

