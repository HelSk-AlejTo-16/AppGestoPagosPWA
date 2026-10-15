package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.cliente.Cliente;
import com.proyecto.servicios.entity.cliente.CuentaBancaria;
import com.proyecto.servicios.exception.BusinessConflictException;
import com.proyecto.servicios.repositorys.cliente.CuentaBancariaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CuentaServiceImplTest {

    @Mock
    private CuentaBancariaRepository cuentaRepository;

    @InjectMocks
    private CuentaServiceImpl cuentaService;

    @Test
    void obtenerSaldo_rechazaCuentaInactiva() {
        CuentaBancaria cuenta = cuentaInactiva();
        when(cuentaRepository.findByNumeroCuenta("1234567890")).thenReturn(Optional.of(cuenta));

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class, () -> cuentaService.obtenerSaldo("1234567890")
        );

        assertEquals("CUENTA_INACTIVA", exception.getCodigo());
    }

    @Test
    void validarCuentaOperable_rechazaClienteInactivoAunqueCuentaEsteActiva() {
        CuentaBancaria cuenta = cuentaInactiva();
        cuenta.setActiva(true);
        when(cuentaRepository.findByNumeroCuenta("1234567890")).thenReturn(Optional.of(cuenta));

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class, () -> cuentaService.validarCuentaOperable("1234567890")
        );

        assertEquals("CUENTA_INACTIVA", exception.getCodigo());
    }

    @Test
    void obtenerSaldo_conDuenio_permiteConsultar() {
        CuentaBancaria cuenta = cuentaActiva("duenio@example.com");
        when(cuentaRepository.findByNumeroCuenta("1234567890")).thenReturn(Optional.of(cuenta));

        var saldo = cuentaService.obtenerSaldo("1234567890", "duenio@example.com", false);
        assertEquals(new java.math.BigDecimal("1000.00"), saldo);
    }

    @Test
    void obtenerSaldo_conUsuarioDistinto_lanzaAccessDeniedException() {
        CuentaBancaria cuenta = cuentaActiva("duenio@example.com");
        when(cuentaRepository.findByNumeroCuenta("1234567890")).thenReturn(Optional.of(cuenta));

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> cuentaService.obtenerSaldo("1234567890", "atacante@example.com", false));
    }

    @Test
    void obtenerSaldo_conAdmin_permiteConsultarCualquierCuenta() {
        CuentaBancaria cuenta = cuentaActiva("duenio@example.com");
        when(cuentaRepository.findByNumeroCuenta("1234567890")).thenReturn(Optional.of(cuenta));

        var saldo = cuentaService.obtenerSaldo("1234567890", "admin@banco.com", true);
        assertEquals(new java.math.BigDecimal("1000.00"), saldo);
    }

    @Test
    void obtenerCuentas_conBanderaActivaTrue_filtraCuentasActivas() {
        when(cuentaRepository.findAllByActivaOrderByCreadoEnDesc(any(Boolean.class), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        var response = cuentaService.obtenerCuentas(true, 0, 10);
        assertEquals(0, response.contenido().size());
        verify(cuentaRepository).findAllByActivaOrderByCreadoEnDesc(eq(true), any());
    }

    @Test
    void obtenerCuentas_conBanderaActivaFalse_filtraCuentasInactivas() {
        when(cuentaRepository.findAllByActivaOrderByCreadoEnDesc(any(Boolean.class), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        var response = cuentaService.obtenerCuentas(false, 0, 10);
        assertEquals(0, response.contenido().size());
        verify(cuentaRepository).findAllByActivaOrderByCreadoEnDesc(eq(false), any());
    }

    @Test
    void obtenerCuentas_sinBandera_obtieneTodas() {
        when(cuentaRepository.findAllByOrderByCreadoEnDesc(any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        var response = cuentaService.obtenerCuentas(null, 0, 10);
        assertEquals(0, response.contenido().size());
        verify(cuentaRepository).findAllByOrderByCreadoEnDesc(any());
    }

    private CuentaBancaria cuentaActiva(String correo) {
        Cliente cliente = new Cliente();
        cliente.setActivo(true);
        com.proyecto.servicios.entity.cliente.Usuario usuario = new com.proyecto.servicios.entity.cliente.Usuario();
        usuario.setCorreo(correo);
        cliente.setUsuario(usuario);

        CuentaBancaria cuenta = new CuentaBancaria();
        cuenta.setCliente(cliente);
        cuenta.setActiva(true);
        cuenta.setNumeroCuenta("1234567890");
        cuenta.setSaldo(new java.math.BigDecimal("1000.00"));
        return cuenta;
    }

    private CuentaBancaria cuentaInactiva() {
        Cliente cliente = new Cliente();
        cliente.setActivo(false);
        CuentaBancaria cuenta = new CuentaBancaria();
        cuenta.setCliente(cliente);
        cuenta.setActiva(false);
        cuenta.setNumeroCuenta("1234567890");
        return cuenta;
    }
}
