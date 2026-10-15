package com.proyecto.servicios.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                                   SecurityErrorHandler securityErrorHandler)
            throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                .authorizeHttpRequests(authorize -> authorize
                        // Endpoints públicos
                        .requestMatchers(HttpMethod.POST, "/auth/login", "/clientes").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/actuator/health").permitAll()

                        // Perfil propio: la identidad sale del token (evita BOLA / IDOR)
                        .requestMatchers(HttpMethod.GET, "/usuarios/me")
                        .hasAnyAuthority("ROLE_USER", "ROLE_ADMIN", "ROLE_RECOVERY")
                        .requestMatchers(HttpMethod.PUT, "/usuarios/me/reactivar").hasAuthority("ROLE_RECOVERY")
                        .requestMatchers("/usuarios/me", "/usuarios/me/password")
                        .hasAnyAuthority("ROLE_USER", "ROLE_ADMIN")
                        .requestMatchers("/clientes/me").hasAnyAuthority("ROLE_USER", "ROLE_ADMIN")

                        // Consultas administrativas (SOLO ROLE_ADMIN)
                        .requestMatchers(HttpMethod.POST, "/clientes/buscar").hasAuthority("ROLE_ADMIN")

                        // Cuentas: cada usuario solo ve las suyas (se filtra en el servicio); admin ve todas
                        .requestMatchers(HttpMethod.POST, "/cuentas/buscar", "/cuentas/saldo")
                        .hasAnyAuthority("ROLE_USER", "ROLE_ADMIN")

                        .anyRequest().hasAnyAuthority("ROLE_USER", "ROLE_ADMIN"))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
