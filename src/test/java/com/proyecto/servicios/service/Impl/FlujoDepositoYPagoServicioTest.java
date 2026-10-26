package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.cliente.Cliente;
import com.proyecto.servicios.entity.cliente.CuentaBancaria;
import com.proyecto.servicios.entity.cliente.Usuario;
import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import com.proyecto.servicios.entity.pagos.EstadoPagoServicio;
import com.proyecto.servicios.entity.pagos.PagoServicioEntity;
import com.proyecto.servicios.exception.BusinessConflictException;
import com.proyecto.servicios.model.cliente.CuentaDepositoRequest;
import com.proyecto.servicios.model.cliente.CuentaSaldoResponse;
import com.proyecto.servicios.model.gestopago.tx.GestoPagoTxResponse;
import com.proyecto.servicios.model.pagos.PagoServicioResponse;
import com.proyecto.servicios.model.pagos.RecargaTelefonicaRequest;
import com.proyecto.servicios.repositorys.cliente.CuentaBancariaRepository;
import com.proyecto.servicios.repositorys.cliente.UsuarioRepository;
import com.proyecto.servicios.repositorys.gestopago.ProductoPostgresRepository;
import com.proyecto.servicios.repositorys.pagos.PagoServicioRepository;
import com.proyecto.servicios.service.GestoPagoTxCommunicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Prueba unitaria e integral que valida el flujo continuo:
 * 1. Agregar fondos a la cuenta de un usuario (Depósito exitoso).
 * 2. Realizar un gasto válido debitando de esos fondos (Recarga telefónica / Pago con autorización PIN).
 * 3. Probar que si el gasto supera los fondos disponibles, se rechaza de forma atómica.
 */
@ExtendWith(MockitoExtension.class)
class FlujoDepositoYPagoServicioTest {

    @Mock
    private CuentaBancariaRepository cuentaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ProductoPostgresRepository productoRepository;

    @Mock
    private PagoServicioRepository pagoRepository;

    @Mock
    private GestoPagoTxCommunicationService txCommunicationService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private CuentaServiceImpl cuentaService;
    private PagoServicioServiceImpl pagoService;

    private Usuario usuario;
    private CuentaBancaria cuenta;
    private ProductoEntity productoTelcel;

    private final String CORREO = "usuario.demo@banco.com";
    private final String NUM_CUENTA = "12345678901234567890";
    private final String PIN_CORRECTO = "MiPassword123";

    @BeforeEach
    void setUp() {
        cuentaService = new CuentaServiceImpl(cuentaRepository);
        pagoService = new PagoServicioServiceImpl(
                pagoRepository, cuentaRepository, usuarioRepository,
                productoRepository, txCommunicationService, passwordEncoder
        );

        Cliente cliente = new Cliente();
        cliente.setIdPersona(UUID.randomUUID());
        cliente.setActivo(true);

        usuario = new Usuario();
        usuario.setIdUsuario(UUID.randomUUID());
        usuario.setCorreo(CORREO);
        usuario.setPassword("$2a$10$hashedPassword");
        usuario.setActivo(true);
        usuario.setIntentosFallidos(0);
        usuario.setCliente(cliente);
        cliente.setUsuario(usuario);

        cuenta = new CuentaBancaria();
        cuenta.setIdBancaria(UUID.randomUUID());
        cuenta.setNumeroCuenta(NUM_CUENTA);
        cuenta.setSaldo(BigDecimal.ZERO); // Saldo inicial: $0.00
        cuenta.setActiva(true);
        cuenta.setCliente(cliente);

        productoTelcel = new ProductoEntity();
        productoTelcel.setId(1);
        productoTelcel.setIdProducto(11);
        productoTelcel.setIdServicio(1);
        productoTelcel.setServicio("TELCEL");
        productoTelcel.setProducto("Paquete 100");
        productoTelcel.setPrecio(100.00);
        productoTelcel.setTipoFront(1);
        productoTelcel.setActivo(true);
    }

    @Test
    @DisplayName("Flujo Completo: Agregar fondos (Depósito) y luego realizar gasto exitoso")
    void testFlujoCompleto_DepositoYGasto() {
        // ==========================================
        // PASO 1: Agregar fondos ($250.00)
        // ==========================================
        when(cuentaRepository.findByNumeroCuenta(NUM_CUENTA)).thenReturn(Optional.of(cuenta));
        when(cuentaRepository.abonarSaldo(eq(cuenta.getIdBancaria()), eq(new BigDecimal("250.00")))).thenReturn(1);

        // Simulamos que tras el abono, la cuenta tiene $250.00
        cuenta.setSaldo(new BigDecimal("250.00"));
        when(cuentaRepository.findById(cuenta.getIdBancaria())).thenReturn(Optional.of(cuenta));

        CuentaDepositoRequest depositoReq = new CuentaDepositoRequest(NUM_CUENTA, new BigDecimal("250.00"), "Apertura");
        CuentaSaldoResponse depositoRes = cuentaService.depositar(depositoReq, CORREO, false);

        assertNotNull(depositoRes);
        assertEquals(new BigDecimal("250.00"), depositoRes.saldo());
        verify(cuentaRepository).abonarSaldo(eq(cuenta.getIdBancaria()), eq(new BigDecimal("250.00")));

        // ==========================================
        // PASO 2: Realizar Gasto (Recarga Telcel $100.00)
        // ==========================================
        when(usuarioRepository.findByCorreoIgnoreCase(CORREO)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(PIN_CORRECTO, usuario.getPassword())).thenReturn(true);
        when(productoRepository.findByIdProducto(11)).thenReturn(Optional.of(productoTelcel));
        
        // El débito atómico tiene éxito (quedan $150.00)
        when(cuentaRepository.debitarSaldo(eq(cuenta.getIdBancaria()), eq(new BigDecimal("100.00")))).thenReturn(1);
        cuenta.setSaldo(new BigDecimal("150.00"));

        when(pagoRepository.saveAndFlush(any(PagoServicioEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pagoRepository.save(any(PagoServicioEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        GestoPagoTxResponse gpResponse = new GestoPagoTxResponse();
        gpResponse.setCodigo("01");
        gpResponse.setFolio("GP-112233");
        gpResponse.setAutorizacion("AUTH-TELCEL-99");
        gpResponse.setTexto("TRANSACCION EXITOSA");
        when(txCommunicationService.ejecutarTransaccion(anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(gpResponse);

        RecargaTelefonicaRequest recargaReq = new RecargaTelefonicaRequest(
                NUM_CUENTA, PIN_CORRECTO, "1", "11", "5512345678", new BigDecimal("100.00")
        );
        PagoServicioResponse pagoRes = pagoService.procesarRecarga(recargaReq, CORREO);

        assertNotNull(pagoRes);
        assertEquals(EstadoPagoServicio.EXITOSO, pagoRes.estado());
        assertEquals("GP-112233", pagoRes.folioTransaccionGp());
        assertEquals("AUTH-TELCEL-99", pagoRes.folioAutorizacion());
        assertEquals(new BigDecimal("150.00"), pagoRes.saldoRestante());

        // ==========================================
        // PASO 3: Intento de Gasto Mayor al Saldo (Insuficiente)
        // ==========================================
        ProductoEntity productoCaro = new ProductoEntity();
        productoCaro.setIdProducto(99);
        productoCaro.setIdServicio(1);
        productoCaro.setProducto("Paquete 200");
        productoCaro.setPrecio(200.00);
        productoCaro.setTipoFront(1);
        productoCaro.setActivo(true);
        when(productoRepository.findByIdProducto(99)).thenReturn(Optional.of(productoCaro));

        // El débito atómico retorna 0 porque 150 < 200
        when(cuentaRepository.debitarSaldo(eq(cuenta.getIdBancaria()), eq(new BigDecimal("200.00")))).thenReturn(0);

        RecargaTelefonicaRequest recargaFallidaReq = new RecargaTelefonicaRequest(
                NUM_CUENTA, PIN_CORRECTO, "1", "99", "5512345678", new BigDecimal("200.00")
        );

        BusinessConflictException ex = assertThrows(BusinessConflictException.class, () ->
                pagoService.procesarRecarga(recargaFallidaReq, CORREO));

        assertEquals("sendTx_insufficientFunds", ex.getCodigo());
        assertEquals(new BigDecimal("150.00"), cuenta.getSaldo()); // El saldo se conserva intacto
    }
}
