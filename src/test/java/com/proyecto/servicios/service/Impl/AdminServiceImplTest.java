package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.cliente.Cliente;
import com.proyecto.servicios.entity.cliente.Contacto;
import com.proyecto.servicios.entity.cliente.CuentaBancaria;
import com.proyecto.servicios.entity.cliente.Usuario;
import com.proyecto.servicios.entity.pagos.EstadoPagoServicio;
import com.proyecto.servicios.exception.BusinessConflictException;
import com.proyecto.servicios.model.admin.AdminAbonoCuentaRequest;
import com.proyecto.servicios.model.admin.AdminResumenResponse;
import com.proyecto.servicios.model.admin.AdminUsuarioBusquedaRequest;
import com.proyecto.servicios.model.admin.AdminUsuarioItemResponse;
import com.proyecto.servicios.model.cliente.CuentaDepositoRequest;
import com.proyecto.servicios.model.cliente.CuentaSaldoResponse;
import com.proyecto.servicios.model.cliente.PaginaResponse;
import com.proyecto.servicios.repositorys.cliente.ClienteRepository;
import com.proyecto.servicios.repositorys.cliente.CuentaBancariaRepository;
import com.proyecto.servicios.repositorys.cliente.UsuarioRepository;
import com.proyecto.servicios.repositorys.pagos.PagoServicioRepository;
import com.proyecto.servicios.service.CuentaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceImplTest {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private CuentaBancariaRepository cuentaBancariaRepository;

    @Mock
    private PagoServicioRepository pagoServicioRepository;

    @Mock
    private CuentaService cuentaService;

    @InjectMocks
    private AdminServiceImpl adminService;

    private UUID idCliente;
    private Cliente cliente;
    private Usuario usuario;
    private CuentaBancaria cuenta;

    @BeforeEach
    void setUp() {
        idCliente = UUID.randomUUID();
        cliente = new Cliente();
        cliente.setIdPersona(idCliente);
        cliente.setNombre("Ana");
        cliente.setApellidoPaterno("López");
        cliente.setCurp("LOPA900101HDFNNN01");
        cliente.setRfc("LOPA900101ABC");
        cliente.setActivo(true);
        cliente.setCreadoEn(OffsetDateTime.now().minusDays(2));

        usuario = new Usuario();
        usuario.setIdUsuario(UUID.randomUUID());
        usuario.setCliente(cliente);
        usuario.setCorreo("ana@ejemplo.com");
        usuario.setRol("ROLE_USER");
        usuario.setActivo(true);
        usuario.setIntentosFallidos(3);
        usuario.setFechaBloqueo(OffsetDateTime.now().plusMinutes(15));
        usuario.setIdSesionActiva("sess-123");
        usuario.setPlataformaActiva("WEB");
        usuario.setFechaInicioSesion(OffsetDateTime.now().minusHours(1));

        Contacto contacto = new Contacto();
        contacto.setIdContactos(UUID.randomUUID());
        contacto.setCliente(cliente);
        contacto.setTelefonoMovil("5512345678");

        cuenta = new CuentaBancaria();
        cuenta.setIdBancaria(UUID.randomUUID());
        cuenta.setCliente(cliente);
        cuenta.setNumeroCuenta("12345678901234567890");
        cuenta.setClabe("012180123456789012");
        cuenta.setSaldo(new BigDecimal("250.00"));
        cuenta.setActiva(true);
        cuenta.setCreadoEn(OffsetDateTime.now().minusDays(1));

        cliente.setUsuario(usuario);
        cliente.setContacto(contacto);
        cliente.setCuentas(List.of(cuenta));
    }

    @Test
    void obtenerResumen_retornaMetricasAgregadas() {
        when(clienteRepository.count()).thenReturn(10L);
        when(clienteRepository.countByActivo(true)).thenReturn(8L);
        when(clienteRepository.countByActivo(false)).thenReturn(2L);
        when(usuarioRepository.countByFechaBloqueoAfter(any())).thenReturn(1L);
        when(cuentaBancariaRepository.count()).thenReturn(12L);
        when(cuentaBancariaRepository.sumarSaldoTotal()).thenReturn(new BigDecimal("15400.50"));
        when(pagoServicioRepository.count()).thenReturn(45L);
        when(pagoServicioRepository.countByEstadoIn(anyCollection())).thenReturn(3L);

        AdminResumenResponse resumen = adminService.obtenerResumen();

        assertEquals(10L, resumen.totalClientes());
        assertEquals(8L, resumen.clientesActivos());
        assertEquals(2L, resumen.clientesInactivos());
        assertEquals(1L, resumen.usuariosBloqueados());
        assertEquals(12L, resumen.totalCuentas());
        assertEquals(new BigDecimal("15400.50"), resumen.saldoTotalSistema());
        assertEquals(45L, resumen.totalPagos());
        assertEquals(3L, resumen.pagosFallidos());
        verify(pagoServicioRepository).countByEstadoIn(List.of(
                EstadoPagoServicio.RECHAZADO,
                EstadoPagoServicio.PENDIENTE_CONCILIACION,
                EstadoPagoServicio.REVERTIDO
        ));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buscarUsuarios_mapeaClientesYCuentas() {
        when(clienteRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cliente)));
        when(cuentaBancariaRepository.findAllByCliente_IdPersonaIn(List.of(idCliente)))
                .thenReturn(List.of(cuenta));

        PaginaResponse<AdminUsuarioItemResponse> pagina = adminService.buscarUsuarios(
                new AdminUsuarioBusquedaRequest("ana", true, "ROLE_USER", false, 0, 10)
        );

        assertEquals(1, pagina.contenido().size());
        AdminUsuarioItemResponse item = pagina.contenido().get(0);
        assertEquals("Ana López", item.nombreCompleto());
        assertEquals("ana@ejemplo.com", item.correo());
        assertTrue(item.bloqueado());
        assertEquals(1, item.cuentas().size());
        assertEquals("12345678901234567890", item.cuentas().get(0).numeroCuenta());
    }

    @Test
    void cambiarEstadoUsuario_impideAutoDesactivacionAdmin() {
        when(clienteRepository.findWithRelationsByIdPersona(idCliente)).thenReturn(Optional.of(cliente));

        assertThrows(
                BusinessConflictException.class,
                () -> adminService.cambiarEstadoUsuario(idCliente, false, "ana@ejemplo.com")
        );
    }

    @Test
    void cambiarEstadoUsuario_desactivaYLimpiarSesion() {
        when(clienteRepository.findWithRelationsByIdPersona(idCliente)).thenReturn(Optional.of(cliente));

        AdminUsuarioItemResponse res = adminService.cambiarEstadoUsuario(idCliente, false, "admin@ejemplo.com");

        assertFalse(res.activo());
        assertFalse(cliente.isActivo());
        assertFalse(usuario.isActivo());
        assertNull(usuario.getIdSesionActiva());
        assertNull(usuario.getPlataformaActiva());
    }

    @Test
    void cambiarRolUsuario_impideQuitarseRolAdminASiMismo() {
        usuario.setRol("ROLE_ADMIN");
        when(clienteRepository.findWithRelationsByIdPersona(idCliente)).thenReturn(Optional.of(cliente));

        assertThrows(
                BusinessConflictException.class,
                () -> adminService.cambiarRolUsuario(idCliente, "ROLE_USER", "ana@ejemplo.com")
        );
    }

    @Test
    void cambiarRolUsuario_actualizaRolCorrectamente() {
        when(clienteRepository.findWithRelationsByIdPersona(idCliente)).thenReturn(Optional.of(cliente));

        AdminUsuarioItemResponse res = adminService.cambiarRolUsuario(idCliente, "ROLE_ADMIN", "admin@ejemplo.com");

        assertEquals("ROLE_ADMIN", res.rol());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void desbloquearUsuario_reiniciaIntentosYFechaBloqueo() {
        when(clienteRepository.findWithRelationsByIdPersona(idCliente)).thenReturn(Optional.of(cliente));

        AdminUsuarioItemResponse res = adminService.desbloquearUsuario(idCliente);

        assertFalse(res.bloqueado());
        assertEquals(0, res.intentosFallidos());
        assertNull(usuario.getFechaBloqueo());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void forzarCierreSesion_limpiaDatosDeSesion() {
        when(clienteRepository.findWithRelationsByIdPersona(idCliente)).thenReturn(Optional.of(cliente));

        AdminUsuarioItemResponse res = adminService.forzarCierreSesion(idCliente);

        assertNull(res.plataformaActiva());
        assertNull(usuario.getIdSesionActiva());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void abonarSaldoACuenta_delegaConEsAdminTrue() {
        CuentaSaldoResponse esperado = new CuentaSaldoResponse("12345678901234567890", new BigDecimal("750.00"));
        when(cuentaService.depositar(any(CuentaDepositoRequest.class), eq("admin@ejemplo.com"), eq(true)))
                .thenReturn(esperado);

        CuentaSaldoResponse res = adminService.abonarSaldoACuenta(
                new AdminAbonoCuentaRequest("12345678901234567890", new BigDecimal("500.00"), "  "),
                "admin@ejemplo.com"
        );

        assertEquals(new BigDecimal("750.00"), res.saldo());
        ArgumentCaptor<CuentaDepositoRequest> captor = ArgumentCaptor.forClass(CuentaDepositoRequest.class);
        verify(cuentaService).depositar(captor.capture(), eq("admin@ejemplo.com"), eq(true));
        assertEquals("Abono administrativo", captor.getValue().concepto());
    }
}
