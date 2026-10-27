package com.proyecto.servicios.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.entity.pagos.EstadoPagoServicio;
import com.proyecto.servicios.exception.GestoPagoTimeoutException;
import com.proyecto.servicios.exception.GlobalExceptionHandler;
import com.proyecto.servicios.model.cliente.PaginaResponse;
import com.proyecto.servicios.model.pagos.PagoBusquedaRequest;
import com.proyecto.servicios.model.pagos.PagoConciliacionRequest;
import com.proyecto.servicios.model.pagos.PagoServicioRequest;
import com.proyecto.servicios.model.pagos.PagoServicioResponse;
import com.proyecto.servicios.model.pagos.RecargaTelefonicaRequest;
import com.proyecto.servicios.service.PagoServicioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PagoServicioControllerTest {

    @Mock
    private PagoServicioService pagoService;

    @InjectMocks
    private PagoServicioController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Authentication authUser = new UsernamePasswordAuthenticationToken(
            "mario@banco.com", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
    );

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /pagos/servicios con cuerpo válido debe devolver HTTP 200 y código 0")
    void testPagarServicio_Exitoso() throws Exception {
        PagoServicioRequest request = new PagoServicioRequest(
                "12345678901234567890", "MiPassword123", "2", "201", "CFE123456789", new BigDecimal("350.00")
        );

        PagoServicioResponse responseDto = new PagoServicioResponse(
                UUID.randomUUID(),
                "12345678901234567890",
                "2",
                "201",
                "CFE",
                "Consumo Doméstico",
                "CFE123456789",
                new BigDecimal("350.00"),
                "UPC1234567",
                EstadoPagoServicio.EXITOSO,
                "AUTH-9999",
                "GP-8888",
                new BigDecimal("150.00"),
                "Pago de servicio realizado con éxito.",
                OffsetDateTime.now()
        );

        when(pagoService.procesarPago(any(PagoServicioRequest.class), eq("mario@banco.com")))
                .thenReturn(responseDto);

        mockMvc.perform(post("/pagos/servicios")
                        .principal(authUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(0))
                .andExpect(jsonPath("$.mensaje").value("Pago de servicio procesado exitosamente."))
                .andExpect(jsonPath("$.datos.upc").value("UPC1234567"))
                .andExpect(jsonPath("$.datos.estado").value("EXITOSO"));
    }

    @Test
    @DisplayName("POST /pagos/servicios con cuenta inválida (no 20 dígitos) debe devolver HTTP 400")
    void testPagarServicio_CuentaInvalida() throws Exception {
        PagoServicioRequest request = new PagoServicioRequest(
                "12345", "MiPassword123", "2", "201", "CFE123456789", new BigDecimal("350.00")
        );

        mockMvc.perform(post("/pagos/servicios")
                        .principal(authUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /pagos/recargas con cuerpo válido debe devolver HTTP 200 y código 0")
    void testRecargarTiempoAire_Exitoso() throws Exception {
        RecargaTelefonicaRequest request = new RecargaTelefonicaRequest(
                "12345678901234567890", "MiPassword123", "1", "11", "5512345678", new BigDecimal("100.00")
        );

        PagoServicioResponse responseDto = new PagoServicioResponse(
                UUID.randomUUID(),
                "12345678901234567890",
                "1",
                "11",
                "TELCEL",
                "Paquete 100",
                "5512345678",
                new BigDecimal("100.00"),
                "UPC9988776",
                EstadoPagoServicio.EXITOSO,
                "AUTH-REC-11",
                "GP-REC-22",
                new BigDecimal("400.00"),
                "Recarga telefónica realizada con éxito.",
                OffsetDateTime.now()
        );

        when(pagoService.procesarRecarga(any(RecargaTelefonicaRequest.class), eq("mario@banco.com")))
                .thenReturn(responseDto);

        mockMvc.perform(post("/pagos/recargas")
                        .principal(authUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(0))
                .andExpect(jsonPath("$.mensaje").value("Recarga telefónica procesada exitosamente."))
                .andExpect(jsonPath("$.datos.upc").value("UPC9988776"))
                .andExpect(jsonPath("$.datos.estado").value("EXITOSO"));
    }

    @Test
    @DisplayName("POST /pagos/recargas con teléfono inválido (no 10 dígitos) debe devolver HTTP 400")
    void testRecargarTiempoAire_TelefonoInvalido() throws Exception {
        RecargaTelefonicaRequest request = new RecargaTelefonicaRequest(
                "12345678901234567890", "MiPassword123", "1", "11", "12345", new BigDecimal("100.00")
        );

        mockMvc.perform(post("/pagos/recargas")
                        .principal(authUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /pagos/historial debe devolver HTTP 200 y estructura paginada")
    void testConsultarHistorial_Exitoso() throws Exception {
        PagoBusquedaRequest busqueda = new PagoBusquedaRequest(
                "12345678901234567890", null, null, null, null, null, 0, 20
        );

        PaginaResponse<PagoServicioResponse> pagina = new PaginaResponse<>(
                Collections.emptyList(), 0, 20, 0, 0, true, true
        );

        when(pagoService.consultarHistorial(any(PagoBusquedaRequest.class), eq("mario@banco.com"), eq(false)))
                .thenReturn(pagina);

        mockMvc.perform(post("/pagos/historial")
                        .principal(authUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(busqueda)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(0))
                .andExpect(jsonPath("$.mensaje").value("Consulta de historial exitosa."))
                .andExpect(jsonPath("$.datos.pagina").value(0));
    }

    @Test
    @DisplayName("POST /pagos/servicios ante timeout del proveedor debe devolver HTTP 504 Gateway Timeout")
    void testPagarServicio_TimeoutDevuelve504() throws Exception {
        PagoServicioRequest request = new PagoServicioRequest(
                "12345678901234567890", "MiPassword123", "2", "201", "CFE123456789", new BigDecimal("350.00")
        );

        when(pagoService.procesarPago(any(PagoServicioRequest.class), eq("mario@banco.com")))
                .thenThrow(new GestoPagoTimeoutException("El proveedor del servicio no respondió a tiempo.", null));

        mockMvc.perform(post("/pagos/servicios")
                        .principal(authUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.codigo").value(92))
                .andExpect(jsonPath("$.mensaje").value("El proveedor del servicio no respondió a tiempo."));
    }

    @Test
    @DisplayName("POST /pagos/conciliar debe devolver HTTP 200 y el comprobante actualizado")
    void testConciliarPago_Exitoso() throws Exception {
        PagoConciliacionRequest request = new PagoConciliacionRequest(
                "UPC1234567", true, "GP-8888", "AUTH-9999", "Confirmado"
        );

        PagoServicioResponse responseDto = new PagoServicioResponse(
                UUID.randomUUID(),
                "12345678901234567890",
                "2",
                "201",
                "CFE",
                "Consumo Doméstico",
                "CFE123456789",
                new BigDecimal("350.00"),
                "UPC1234567",
                EstadoPagoServicio.EXITOSO,
                "AUTH-9999",
                "GP-8888",
                new BigDecimal("150.00"),
                "Conciliación completada: la operación fue confirmada como exitosa.",
                OffsetDateTime.now()
        );

        when(pagoService.conciliarPago(any(PagoConciliacionRequest.class), eq("mario@banco.com"), eq(false)))
                .thenReturn(responseDto);

        mockMvc.perform(post("/pagos/conciliar")
                        .principal(authUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(0))
                .andExpect(jsonPath("$.datos.upc").value("UPC1234567"))
                .andExpect(jsonPath("$.datos.estado").value("EXITOSO"));
    }
}
