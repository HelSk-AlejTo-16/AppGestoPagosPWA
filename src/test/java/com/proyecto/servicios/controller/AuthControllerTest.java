package com.proyecto.servicios.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.exception.GlobalExceptionHandler;
import com.proyecto.servicios.model.cliente.LoginRequest;
import com.proyecto.servicios.model.cliente.LoginResponse;
import com.proyecto.servicios.model.cliente.Reenviar2faRequest;
import com.proyecto.servicios.model.cliente.RestablecerPasswordRequest;
import com.proyecto.servicios.model.cliente.SolicitarRecuperacionRequest;
import com.proyecto.servicios.model.cliente.Verificar2faRequest;
import com.proyecto.servicios.service.AuthService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /auth/login retorna 200 y respuesta 2FA")
    void testLogin_Exitoso() throws Exception {
        LoginRequest request = new LoginRequest("user@banco.com", "Password123", "MOVIL");
        LoginResponse response = LoginResponse.para2fa("temp-ticket-jwt", 300, false, null, "123456");

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(0))
                .andExpect(jsonPath("$.datos.requiere2fa").value(true))
                .andExpect(jsonPath("$.datos.token").value("temp-ticket-jwt"))
                .andExpect(jsonPath("$.datos.codigoOtpSimulado").value("123456"));
    }

    @Test
    @DisplayName("POST /auth/2fa/verificar con código válido retorna 200 y token de sesión")
    void testVerificar2fa_Exitoso() throws Exception {
        Verificar2faRequest request = new Verificar2faRequest("temp-ticket-jwt", "123456", false);
        LoginResponse response = LoginResponse.exitoso("final-jwt-token", 3600, "FULL");

        when(authService.verificar2fa(any(Verificar2faRequest.class))).thenReturn(response);

        mockMvc.perform(post("/auth/2fa/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(0))
                .andExpect(jsonPath("$.datos.token").value("final-jwt-token"))
                .andExpect(jsonPath("$.datos.modoAcceso").value("FULL"));
    }

    @Test
    @DisplayName("POST /auth/logout llama al servicio de cierre de sesión")
    void testLogout_Exitoso() throws Exception {
        Authentication auth = new UsernamePasswordAuthenticationToken("user@banco.com", null);

        mockMvc.perform(post("/auth/logout").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(0))
                .andExpect(jsonPath("$.mensaje").value("Sesión cerrada exitosamente."));

        verify(authService).logout("user@banco.com");
    }

    @Test
    @DisplayName("POST /auth/2fa/reenviar genera nuevo código 2FA")
    void testReenviar2fa_Exitoso() throws Exception {
        Reenviar2faRequest request = new Reenviar2faRequest("temp-ticket-jwt");
        LoginResponse response = LoginResponse.para2fa("nuevo-ticket-jwt", 300, false, null, null);

        when(authService.reenviar2fa(any(Reenviar2faRequest.class))).thenReturn(response);

        mockMvc.perform(post("/auth/2fa/reenviar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(0))
                .andExpect(jsonPath("$.datos.token").value("nuevo-ticket-jwt"));
    }

    @Test
    @DisplayName("POST /auth/password/solicitar-codigo envía código de recuperación")
    void testSolicitarCodigoPassword_Exitoso() throws Exception {
        SolicitarRecuperacionRequest request = new SolicitarRecuperacionRequest("user@banco.com");

        mockMvc.perform(post("/auth/password/solicitar-codigo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(0));

        verify(authService).solicitarRecuperacionPassword(any(SolicitarRecuperacionRequest.class));
    }

    @Test
    @DisplayName("POST /auth/password/restablecer restablece contraseña con OTP")
    void testRestablecerPassword_Exitoso() throws Exception {
        RestablecerPasswordRequest request = new RestablecerPasswordRequest(
                "user@banco.com", "123456", "Segura123!");

        mockMvc.perform(post("/auth/password/restablecer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value(0));

        verify(authService).restablecerPassword(any(RestablecerPasswordRequest.class));
    }
}
