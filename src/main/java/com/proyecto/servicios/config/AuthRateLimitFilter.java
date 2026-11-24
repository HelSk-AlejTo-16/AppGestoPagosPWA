package com.proyecto.servicios.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Filtro ligero de limitación de tasa (Rate Limiting) por dirección IP para proteger
 * los endpoints públicos de Login, Registro, 2FA y Recuperación contra fuerza bruta y bots.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_LOGIN_POR_MINUTO = 15;
    private static final int MAX_REGISTRO_POR_5_MIN = 8;
    private static final long UN_MINUTO_MS = 60_000L;
    private static final long CINCO_MINUTOS_MS = 300_000L;

    private final SecurityErrorHandler securityErrorHandler;
    private final Map<String, Deque<Long>> ventanasPorIpYRuta = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(SecurityErrorHandler securityErrorHandler) {
        this.securityErrorHandler = securityErrorHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI();
        }

        boolean esRegistro = "/clientes".equals(path);
        boolean esAuthPublico = path.startsWith("/auth/login")
                || path.startsWith("/auth/2fa")
                || path.startsWith("/auth/password");

        if (!esRegistro && !esAuthPublico) {
            chain.doFilter(request, response);
            return;
        }

        String ip = extraerIpCliente(request);
        String bucketKey = (esRegistro ? "REG:" : "AUTH:") + ip;
        int limite = esRegistro ? MAX_REGISTRO_POR_5_MIN : MAX_LOGIN_POR_MINUTO;
        long ventanaMs = esRegistro ? CINCO_MINUTOS_MS : UN_MINUTO_MS;

        if (!permitirSolicitud(bucketKey, limite, ventanaMs)) {
            response.setHeader("Retry-After", esRegistro ? "60" : "30");
            securityErrorHandler.escribirDemasiadasSolicitudes(
                    response,
                    "Has realizado demasiadas solicitudes seguidas. Por seguridad, espera unos segundos antes de intentar nuevamente."
            );
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean permitirSolicitud(String key, int limite, long ventanaMs) {
        long ahora = System.currentTimeMillis();
        if (ventanasPorIpYRuta.size() > 5_000) {
            ventanasPorIpYRuta.entrySet().removeIf(e -> {
                Long ultimo = e.getValue().peekLast();
                return ultimo == null || (ahora - ultimo) > CINCO_MINUTOS_MS;
            });
        }

        Deque<Long> marcas = ventanasPorIpYRuta.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        synchronized (marcas) {
            while (!marcas.isEmpty() && (ahora - marcas.peekFirst()) > ventanaMs) {
                marcas.pollFirst();
            }
            if (marcas.size() >= limite) {
                return false;
            }
            marcas.addLast(ahora);
            return true;
        }
    }

    private String extraerIpCliente(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int coma = xff.indexOf(',');
            return (coma > 0 ? xff.substring(0, coma) : xff).trim();
        }
        String remote = request.getRemoteAddr();
        return (remote != null && !remote.isBlank()) ? remote : "unknown";
    }
}
