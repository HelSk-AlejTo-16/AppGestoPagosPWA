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
