package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.config.SecurityErrorHandler;
import com.proyecto.servicios.entity.cliente.Cliente;
import com.proyecto.servicios.entity.cliente.CuentaBancaria;
import com.proyecto.servicios.entity.cliente.Usuario;
import com.proyecto.servicios.exception.ErrorValidacionException;
import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.model.cliente.ClienteBusquedaRequest;
import com.proyecto.servicios.model.cliente.CuentaBusquedaRequest;
import com.proyecto.servicios.model.cliente.CuentaResponse;
import com.proyecto.servicios.model.cliente.PaginaResponse;
import com.proyecto.servicios.repositorys.cliente.ClienteRepository;
import com.proyecto.servicios.repositorys.cliente.CuentaBancariaRepository;
import com.proyecto.servicios.repositorys.cliente.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cubre las consultas con filtros en el cuerpo, los endpoints /me y los errores de seguridad. */
@ExtendWith(MockitoExtension.class)
class BusquedaYIdentidadTest {

    @Mock
    private CuentaBancariaRepository cuentaRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private com.proyecto.servicios.service.ClienteService clienteServiceMock;

    @InjectMocks
    private CuentaServiceImpl cuentaService;

    @Test
    void cuentas_buscar_conFiltros_consultaElRepositorio() {
        CuentaBancaria cuenta = cuenta("duenio@example.com");
        when(cuentaRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cuenta)));

        PaginaResponse<CuentaResponse> pagina = cuentaService.buscar(
                new CuentaBusquedaRequest("1234567890", null, null, true, null, null),
                "duenio@example.com", false);

        assertEquals(1, pagina.contenido().size());
        assertEquals("1234567890", pagina.contenido().get(0).numeroCuenta());
    }

    @Test
    void cuentas_buscar_rechazaTamanoFueraDeRango() {
        assertThrows(ErrorValidacionException.class, () -> cuentaService.buscar(
                new CuentaBusquedaRequest(null, null, null, null, 0, 500), "admin@banco.com", true));
        verify(cuentaRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void clientes_buscar_rechazaRangoDeFechasInvalido() {
        ClienteServiceImpl clienteService = new ClienteServiceImpl(
                clienteRepository, cuentaRepository, usuarioRepository, passwordEncoder);
        ClienteBusquedaRequest criterios = new ClienteBusquedaRequest(
                null, null, null, null, null, null,
                LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 1), null, null);

        assertThrows(ErrorValidacionException.class, () -> clienteService.buscar(criterios));
    }

    @Test
    void clientes_buscar_aplicaValoresPorDefectoDePaginacion() {
        ClienteBusquedaRequest criterios = new ClienteBusquedaRequest(
                null, null, null, null, null, null, null, null, null, null);

        assertEquals(0, criterios.pagina());
        assertEquals(20, criterios.tamano());
    }

    @Test
    void clientes_buscar_sinResultados_devuelvePaginaVacia() {
        ClienteServiceImpl clienteService = new ClienteServiceImpl(
                clienteRepository, cuentaRepository, usuarioRepository, passwordEncoder);
        Page<Cliente> vacia = new PageImpl<>(List.of());
        when(clienteRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(vacia);

        var pagina = clienteService.buscar(new ClienteBusquedaRequest(
                null, null, null, null, null, null, null, null, null, null));

        assertTrue(pagina.contenido().isEmpty());
    }

    @Test
    void usuarios_desactivarMiUsuario_usaLaIdentidadDelToken() {
        UsuarioServiceImpl usuarioService = new UsuarioServiceImpl(
                usuarioRepository, passwordEncoder, clienteServiceMock);
        UUID id = UUID.randomUUID();
        Usuario usuario = usuario(id, "yo@example.com");
        when(usuarioRepository.findByCorreoIgnoreCase("yo@example.com")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByIdUsuario(id)).thenReturn(Optional.of(usuario));

        usuarioService.desactivarMiUsuario("yo@example.com");

        assertEquals(false, usuario.isActivo());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void usuarios_desactivarMiUsuario_conTokenDeUsuarioInexistente_lanzaNoEncontrado() {
        UsuarioServiceImpl usuarioService = new UsuarioServiceImpl(
                usuarioRepository, passwordEncoder, clienteServiceMock);
        when(usuarioRepository.findByCorreoIgnoreCase("fantasma@example.com")).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> usuarioService.desactivarMiUsuario("fantasma@example.com"));
    }

    @Test
    void seguridad_401_devuelveJsonConLaEstructuraUniforme() throws Exception {
        SecurityErrorHandler handler = new SecurityErrorHandler(new ObjectMapper());
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(new MockHttpServletRequest(), response, new BadCredentialsException("x"));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"codigo\":401"));
        assertTrue(response.getContentAsString().contains("\"mensaje\""));
    }

    @Test
    void seguridad_403_devuelveJsonConLaEstructuraUniforme() throws Exception {
        SecurityErrorHandler handler = new SecurityErrorHandler(new ObjectMapper());
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("x"));

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"codigo\":403"));
    }

    private CuentaBancaria cuenta(String correo) {
        Usuario usuario = usuario(UUID.randomUUID(), correo);
        Cliente cliente = new Cliente();
        cliente.setIdPersona(UUID.randomUUID());
        cliente.setUsuario(usuario);
        CuentaBancaria cuenta = new CuentaBancaria();
        cuenta.setIdBancaria(UUID.randomUUID());
        cuenta.setCliente(cliente);
        cuenta.setNumeroCuenta("1234567890");
        cuenta.setClabe("012345678901234567");
        cuenta.setSaldo(new BigDecimal("100.00"));
        cuenta.setActiva(true);
        return cuenta;
    }

    private Usuario usuario(UUID id, String correo) {
        Cliente cliente = new Cliente();
        cliente.setIdPersona(UUID.randomUUID());
        cliente.setActivo(true);
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(id);
        usuario.setCorreo(correo);
        usuario.setActivo(true);
        usuario.setCliente(cliente);
        return usuario;
    }
}

