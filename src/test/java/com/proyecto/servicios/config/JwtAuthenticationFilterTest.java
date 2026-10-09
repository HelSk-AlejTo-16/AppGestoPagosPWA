package com.proyecto.servicios.config;

import com.proyecto.servicios.service.JwtService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final UserDetailsService userDetailsService = mock(UserDetailsService.class);
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(jwtService, userDetailsService);

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aceptaTokenDeRecuperacionMientrasLaCuentaSigueInactiva() throws Exception {
        autenticar("recovery-token", true, "ROLE_RECOVERY");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("ROLE_RECOVERY", authentication.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void rechazaTokenCompletoCuandoLaCuentaYaEstaInactiva() throws Exception {
        autenticar("full-token", false, "ROLE_RECOVERY");

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void rechazaTokenDeRecuperacionDespuesDeReactivarLaCuenta() throws Exception {
        autenticar("recovery-token", true, "ROLE_USER");

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private void autenticar(String token, boolean recoveryToken, String authority) throws Exception {
        SecurityContextHolder.clearContext();
        UserDetails user = User.withUsername("cliente@example.com")
                .password("hash")
                .authorities(authority)
                .build();
        when(jwtService.extraerCorreo(token)).thenReturn("cliente@example.com");
        when(jwtService.esTokenDeRecuperacion(token)).thenReturn(recoveryToken);
        when(jwtService.esValido(token, user.getUsername())).thenReturn(true);
        when(userDetailsService.loadUserByUsername("cliente@example.com")).thenReturn(user);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (servletRequest, servletResponse) -> { };

        filter.doFilter(request, response, chain);
    }
}
