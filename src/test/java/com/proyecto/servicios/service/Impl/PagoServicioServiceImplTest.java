package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.cliente.Cliente;
import com.proyecto.servicios.entity.cliente.CuentaBancaria;
import com.proyecto.servicios.entity.cliente.Usuario;
import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import com.proyecto.servicios.entity.pagos.EstadoPagoServicio;
import com.proyecto.servicios.entity.pagos.PagoServicioEntity;
import com.proyecto.servicios.exception.AccesoNoAutorizadoException;
import com.proyecto.servicios.exception.BusinessConflictException;
import com.proyecto.servicios.exception.ErrorValidacionException;
import com.proyecto.servicios.exception.GestoPagoTimeoutException;
import com.proyecto.servicios.model.gestopago.tx.GestoPagoTxResponse;
import com.proyecto.servicios.model.pagos.PagoConciliacionRequest;
import com.proyecto.servicios.model.pagos.PagoServicioRequest;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PagoServicioServiceImplTest {

    @Mock
    private PagoServicioRepository pagoRepository;

    @Mock
    private CuentaBancariaRepository cuentaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ProductoPostgresRepository productoRepository;

    @Mock
    private GestoPagoTxCommunicationService txCommunicationService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PagoServicioServiceImpl pagoService;

    private Usuario usuarioMock;
    private CuentaBancaria cuentaMock;
    private ProductoEntity productoServicioMock;
    private ProductoEntity productoRecargaMock;
    private final String correo = "mario.bross@banco.com";
    private final String numCuenta = "12345678901234567890";

    @BeforeEach
    void setUp() {
        Cliente cliente = new Cliente();
        cliente.setIdPersona(UUID.randomUUID());
        cliente.setActivo(true);

        usuarioMock = new Usuario();
        usuarioMock.setIdUsuario(UUID.randomUUID());
        usuarioMock.setCorreo(correo);
        usuarioMock.setPassword("$2a$10$hashedPasswordMock");
        usuarioMock.setActivo(true);
        usuarioMock.setIntentosFallidos(0);
        usuarioMock.setCliente(cliente);
        cliente.setUsuario(usuarioMock);

        cuentaMock = new CuentaBancaria();
        cuentaMock.setIdBancaria(UUID.randomUUID());
        cuentaMock.setNumeroCuenta(numCuenta);
        cuentaMock.setSaldo(new BigDecimal("500.00"));
        cuentaMock.setActiva(true);
        cuentaMock.setCliente(cliente);

        // Producto de Servicio (CFE, Agua, etc.) -> tipoFront = 2
        productoServicioMock = new ProductoEntity();
        productoServicioMock.setId(1);
        productoServicioMock.setIdProducto(11);
        productoServicioMock.setIdServicio(1);
        productoServicioMock.setServicio("CFE");
        productoServicioMock.setProducto("CFE Suministrador");
        productoServicioMock.setPrecio(100.00);
        productoServicioMock.setTipoFront(2);
        productoServicioMock.setActivo(true);

        // Producto de Recarga Telefónica -> tipoFront = 1
        productoRecargaMock = new ProductoEntity();
        productoRecargaMock.setId(2);
        productoRecargaMock.setIdProducto(22);
        productoRecargaMock.setIdServicio(2);
        productoRecargaMock.setServicio("TELCEL");
        productoRecargaMock.setProducto("Paquete Amigo 100");
        productoRecargaMock.setPrecio(100.00);
        productoRecargaMock.setTipoFront(1);
        productoRecargaMock.setActivo(true);
    }

    @Test
    @DisplayName("Pago de servicio exitoso: debita atómicamente, llama a GestoPago y devuelve comprobante")
    void testProcesarPago_Exitoso() {
        PagoServicioRequest request = new PagoServicioRequest(
                numCuenta, "MiPassword123", "1", "11", "CFE123456789", new BigDecimal("100.00")
        );

        when(usuarioRepository.findByCorreoIgnoreCase(correo)).thenReturn(Optional.of(usuarioMock));
        when(passwordEncoder.matches("MiPassword123", usuarioMock.getPassword())).thenReturn(true);
        when(cuentaRepository.findByNumeroCuenta(numCuenta)).thenReturn(Optional.of(cuentaMock));
        when(productoRepository.findByIdProducto(11)).thenReturn(Optional.of(productoServicioMock));
        when(cuentaRepository.debitarSaldo(eq(cuentaMock.getIdBancaria()), eq(new BigDecimal("100.00")))).thenReturn(1);
        when(cuentaRepository.findById(cuentaMock.getIdBancaria())).thenReturn(Optional.of(cuentaMock));

        when(pagoRepository.saveAndFlush(any(PagoServicioEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pagoRepository.save(any(PagoServicioEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        GestoPagoTxResponse gpResponse = new GestoPagoTxResponse();
        gpResponse.setCodigo("01");
        gpResponse.setFolio("GP-998877");
        gpResponse.setAutorizacion("AUTH-1234");
        gpResponse.setTexto("APROBADA");
        when(txCommunicationService.ejecutarTransaccion(anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(gpResponse);

        PagoServicioResponse response = pagoService.procesarPago(request, correo);

        assertNotNull(response);
        assertEquals(EstadoPagoServicio.EXITOSO, response.estado());
        assertEquals("GP-998877", response.folioTransaccionGp());
        assertEquals("AUTH-1234", response.folioAutorizacion());
        assertEquals(new BigDecimal("100.00"), response.monto());

        // Verificar que NO se hizo reembolso/abono
        verify(cuentaRepository, never()).abonarSaldo(any(), any());
    }

    @Test
    @DisplayName("Recarga telefónica exitosa: valida 10 dígitos, debita atómicamente y aprueba")
    void testProcesarRecarga_Exitoso() {
        RecargaTelefonicaRequest request = new RecargaTelefonicaRequest(
                numCuenta, "MiPassword123", "2", "22", "5512345678", new BigDecimal("100.00")
        );

        when(usuarioRepository.findByCorreoIgnoreCase(correo)).thenReturn(Optional.of(usuarioMock));
        when(passwordEncoder.matches("MiPassword123", usuarioMock.getPassword())).thenReturn(true);
        when(cuentaRepository.findByNumeroCuenta(numCuenta)).thenReturn(Optional.of(cuentaMock));
        when(productoRepository.findByIdProducto(22)).thenReturn(Optional.of(productoRecargaMock));
        when(cuentaRepository.debitarSaldo(eq(cuentaMock.getIdBancaria()), eq(new BigDecimal("100.00")))).thenReturn(1);
        when(cuentaRepository.findById(cuentaMock.getIdBancaria())).thenReturn(Optional.of(cuentaMock));

        when(pagoRepository.saveAndFlush(any(PagoServicioEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pagoRepository.save(any(PagoServicioEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        GestoPagoTxResponse gpResponse = new GestoPagoTxResponse();
        gpResponse.setCodigo("01");
        gpResponse.setFolio("GP-REC-555");
        gpResponse.setAutorizacion("AUTH-REC-999");
        gpResponse.setTexto("APROBADA");
        when(txCommunicationService.ejecutarTransaccion(anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(gpResponse);

        PagoServicioResponse response = pagoService.procesarRecarga(request, correo);

        assertNotNull(response);
        assertEquals(EstadoPagoServicio.EXITOSO, response.estado());
        assertEquals("GP-REC-555", response.folioTransaccionGp());
        assertEquals("AUTH-REC-999", response.folioAutorizacion());
        verify(cuentaRepository, never()).abonarSaldo(any(), any());
    }

    @Test
    @DisplayName("Pago de servicios rechaza si el producto seleccionado es una recarga (mensaje amigable sin endpoints)")
    void testProcesarPago_ProductoEsRecarga_DebeRechazar() {
        PagoServicioRequest request = new PagoServicioRequest(
                numCuenta, "MiPassword123", "2", "22", "5512345678", new BigDecimal("100.00")
        );

        when(usuarioRepository.findByCorreoIgnoreCase(correo)).thenReturn(Optional.of(usuarioMock));
        when(passwordEncoder.matches("MiPassword123", usuarioMock.getPassword())).thenReturn(true);
        when(cuentaRepository.findByNumeroCuenta(numCuenta)).thenReturn(Optional.of(cuentaMock));
        when(productoRepository.findByIdProducto(22)).thenReturn(Optional.of(productoRecargaMock));

        BusinessConflictException ex = assertThrows(BusinessConflictException.class, () ->
                pagoService.procesarPago(request, correo));

        assertEquals("OPERACION_NO_PERMITIDA", ex.getCodigo());
        assertTrue(ex.getMessage().contains("recarga de tiempo aire"));
        assertFalse(ex.getMessage().contains("/pagos/recargas"), "No debe exponer rutas ni endpoints");
        verify(cuentaRepository, never()).debitarSaldo(any(), any());
    }

    @Test
    @DisplayName("Recarga telefónica rechaza si el producto seleccionado es un servicio (mensaje amigable sin endpoints)")
    void testProcesarRecarga_ProductoEsServicio_DebeRechazar() {
        RecargaTelefonicaRequest request = new RecargaTelefonicaRequest(
                numCuenta, "MiPassword123", "1", "11", "5512345678", new BigDecimal("100.00")
        );

        when(usuarioRepository.findByCorreoIgnoreCase(correo)).thenReturn(Optional.of(usuarioMock));
        when(passwordEncoder.matches("MiPassword123", usuarioMock.getPassword())).thenReturn(true);
        when(cuentaRepository.findByNumeroCuenta(numCuenta)).thenReturn(Optional.of(cuentaMock));
        when(productoRepository.findByIdProducto(11)).thenReturn(Optional.of(productoServicioMock));

        BusinessConflictException ex = assertThrows(BusinessConflictException.class, () ->
                pagoService.procesarRecarga(request, correo));

        assertEquals("OPERACION_NO_PERMITIDA", ex.getCodigo());
        assertTrue(ex.getMessage().contains("pago de servicio"));
        assertFalse(ex.getMessage().contains("/pagos/servicios"), "No debe exponer rutas ni endpoints");
        verify(cuentaRepository, never()).debitarSaldo(any(), any());
    }

    @Test
    @DisplayName("PIN inválido: debe incrementar intentos fallidos, lanzar AccesoNoAutorizadoException (HTTP 401) y no tocar el saldo")
    void testProcesarPago_PinInvalido() {
        PagoServicioRequest request = new PagoServicioRequest(
                numCuenta, "WrongPassword", "1", "11", "CFE123456789", new BigDecimal("100.00")
        );

        when(usuarioRepository.findByCorreoIgnoreCase(correo)).thenReturn(Optional.of(usuarioMock));
        when(passwordEncoder.matches("WrongPassword", usuarioMock.getPassword())).thenReturn(false);

        AccesoNoAutorizadoException ex = assertThrows(AccesoNoAutorizadoException.class, () ->
                pagoService.procesarPago(request, correo));

        assertEquals("PIN_INVALIDO", ex.getCodigo());
        assertEquals(401, ex.getStatus());
        assertTrue(ex.getMessage().contains("Intentos restantes: 2"));
        assertEquals(1, usuarioMock.getIntentosFallidos());
        verify(usuarioRepository).save(usuarioMock);
        verify(cuentaRepository, never()).debitarSaldo(any(), any());
    }

    @Test
    @DisplayName("PIN bloqueado: al 3er fallo consecutivo bloquea al usuario por 15 minutos")
    void testProcesarPago_BloqueoPorIntentos() {
        usuarioMock.setIntentosFallidos(2);
        PagoServicioRequest request = new PagoServicioRequest(
                numCuenta, "WrongPassword", "1", "11", "CFE123456789", new BigDecimal("100.00")
        );

        when(usuarioRepository.findByCorreoIgnoreCase(correo)).thenReturn(Optional.of(usuarioMock));
        when(passwordEncoder.matches("WrongPassword", usuarioMock.getPassword())).thenReturn(false);

        BusinessConflictException ex = assertThrows(BusinessConflictException.class, () ->
                pagoService.procesarPago(request, correo));

        assertEquals("USUARIO_BLOQUEADO", ex.getCodigo());
        assertEquals(3, usuarioMock.getIntentosFallidos());
        assertNotNull(usuarioMock.getFechaBloqueo());
        verify(cuentaRepository, never()).debitarSaldo(any(), any());
    }

    @Test
    @DisplayName("Cuenta ajena: debe lanzar AccessDeniedException")
    void testProcesarPago_CuentaNoPerteneceAlUsuario() {
        Cliente clienteAjeno = new Cliente();
        Usuario usuarioAjeno = new Usuario();
        usuarioAjeno.setCorreo("otro.usuario@banco.com");
        clienteAjeno.setUsuario(usuarioAjeno);
        cuentaMock.setCliente(clienteAjeno);

        PagoServicioRequest request = new PagoServicioRequest(
                numCuenta, "Password123", "1", "11", "CFE123456789", new BigDecimal("100.00")
        );

        when(usuarioRepository.findByCorreoIgnoreCase(correo)).thenReturn(Optional.of(usuarioMock));
        when(passwordEncoder.matches("Password123", usuarioMock.getPassword())).thenReturn(true);
        when(cuentaRepository.findByNumeroCuenta(numCuenta)).thenReturn(Optional.of(cuentaMock));

        assertThrows(AccessDeniedException.class, () ->
                pagoService.procesarPago(request, correo));
        verify(cuentaRepository, never()).debitarSaldo(any(), any());
    }

    @Test
    @DisplayName("Saldo insuficiente: debe arrojar sendTx_insufficientFunds con mensaje amigable")
    void testProcesarPago_SaldoInsuficiente() {
        PagoServicioRequest request = new PagoServicioRequest(
                numCuenta, "Password123", "1", "11", "CFE123456789", new BigDecimal("100.00")
        );

        when(usuarioRepository.findByCorreoIgnoreCase(correo)).thenReturn(Optional.of(usuarioMock));
        when(passwordEncoder.matches("Password123", usuarioMock.getPassword())).thenReturn(true);
        when(cuentaRepository.findByNumeroCuenta(numCuenta)).thenReturn(Optional.of(cuentaMock));
        when(productoRepository.findByIdProducto(11)).thenReturn(Optional.of(productoServicioMock));
        when(cuentaRepository.debitarSaldo(eq(cuentaMock.getIdBancaria()), eq(new BigDecimal("100.00")))).thenReturn(0);

        BusinessConflictException ex = assertThrows(BusinessConflictException.class, () ->
                pagoService.procesarPago(request, correo));

        assertEquals("sendTx_insufficientFunds", ex.getCodigo());
        assertTrue(ex.getMessage().contains("saldo disponible suficiente"));
        verify(txCommunicationService, never()).ejecutarTransaccion(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Rechazo de GestoPago: DEBE ejecutar compensación inmediata / rollback (abonarSaldo)")
    void testProcesarPago_RechazoProveedor_AplicaRollback() {
        PagoServicioRequest request = new PagoServicioRequest(
                numCuenta, "Password123", "1", "11", "CFE123456789", new BigDecimal("100.00")
        );

        when(usuarioRepository.findByCorreoIgnoreCase(correo)).thenReturn(Optional.of(usuarioMock));
        when(passwordEncoder.matches("Password123", usuarioMock.getPassword())).thenReturn(true);
        when(cuentaRepository.findByNumeroCuenta(numCuenta)).thenReturn(Optional.of(cuentaMock));
        when(productoRepository.findByIdProducto(11)).thenReturn(Optional.of(productoServicioMock));
        when(cuentaRepository.debitarSaldo(eq(cuentaMock.getIdBancaria()), eq(new BigDecimal("100.00")))).thenReturn(1);
        when(cuentaRepository.findById(cuentaMock.getIdBancaria())).thenReturn(Optional.of(cuentaMock));
        when(pagoRepository.saveAndFlush(any(PagoServicioEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        GestoPagoTxResponse rechazo = new GestoPagoTxResponse();
        rechazo.setCodigo("05");
        rechazo.setTexto("CARRIER TEMPORALMENTE NO DISPONIBLE");
        when(txCommunicationService.ejecutarTransaccion(anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(rechazo);

        BusinessConflictException ex = assertThrows(BusinessConflictException.class, () ->
                pagoService.procesarPago(request, correo));

        assertEquals("GESTOPAGO_RECHAZADO", ex.getCodigo());
        assertTrue(ex.getMessage().contains("reembolsado automáticamente"));

        // VERIFICACIÓN CLAVE: Se ejecutó el abono / rollback de compensación
        verify(cuentaRepository).abonarSaldo(eq(cuentaMock.getIdBancaria()), eq(new BigDecimal("100.00")));

        // Y se guardó el pago como RECHAZADO
        ArgumentCaptor<PagoServicioEntity> captor = ArgumentCaptor.forClass(PagoServicioEntity.class);
        verify(pagoRepository).save(captor.capture());
        assertEquals(EstadoPagoServicio.RECHAZADO, captor.getValue().getEstado());
        assertEquals("05", captor.getValue().getCodigoRespuestaProveedor());
    }

    @Test
    @DisplayName("Timeout de proveedor: DEBE marcar como PENDIENTE_CONCILIACION sin reembolso ciego y lanzar GestoPagoTimeoutException (HTTP 504)")
    void testProcesarPago_TimeoutProveedor_MarcaPendienteConciliacion() {
        PagoServicioRequest request = new PagoServicioRequest(
                numCuenta, "Password123", "1", "11", "CFE123456789", new BigDecimal("100.00")
        );

        when(usuarioRepository.findByCorreoIgnoreCase(correo)).thenReturn(Optional.of(usuarioMock));
        when(passwordEncoder.matches("Password123", usuarioMock.getPassword())).thenReturn(true);
        when(cuentaRepository.findByNumeroCuenta(numCuenta)).thenReturn(Optional.of(cuentaMock));
        when(productoRepository.findByIdProducto(11)).thenReturn(Optional.of(productoServicioMock));
        when(cuentaRepository.debitarSaldo(eq(cuentaMock.getIdBancaria()), eq(new BigDecimal("100.00")))).thenReturn(1);
        when(cuentaRepository.findById(cuentaMock.getIdBancaria())).thenReturn(Optional.of(cuentaMock));
        when(pagoRepository.saveAndFlush(any(PagoServicioEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        when(txCommunicationService.ejecutarTransaccion(anyString(), anyString(), anyString(), any(), anyString()))
                .thenThrow(new GestoPagoTimeoutException("Read timed out", null));

        GestoPagoTimeoutException ex = assertThrows(GestoPagoTimeoutException.class, () ->
                pagoService.procesarPago(request, correo));

        assertTrue(ex.getMessage().contains("no respondió a tiempo"));

        // VERIFICACIÓN: NO se debe abonar a ciegas para evitar saldo doble
        verify(cuentaRepository, never()).abonarSaldo(any(), any());

        // Y se guardó el pago como PENDIENTE_CONCILIACION
        ArgumentCaptor<PagoServicioEntity> captor = ArgumentCaptor.forClass(PagoServicioEntity.class);
        verify(pagoRepository).save(captor.capture());
        assertEquals(EstadoPagoServicio.PENDIENTE_CONCILIACION, captor.getValue().getEstado());
    }

    @Test
    @DisplayName("Monto fijo: debe rechazar si el monto solicitado no coincide con el precio del catálogo")
    void testProcesarPago_MontoNoCoincide() {
        PagoServicioRequest request = new PagoServicioRequest(
                numCuenta, "Password123", "1", "11", "CFE123456789", new BigDecimal("80.00")
        );

        when(usuarioRepository.findByCorreoIgnoreCase(correo)).thenReturn(Optional.of(usuarioMock));
        when(passwordEncoder.matches("Password123", usuarioMock.getPassword())).thenReturn(true);
        when(cuentaRepository.findByNumeroCuenta(numCuenta)).thenReturn(Optional.of(cuentaMock));
        when(productoRepository.findByIdProducto(11)).thenReturn(Optional.of(productoServicioMock));

        BusinessConflictException ex = assertThrows(BusinessConflictException.class, () ->
                pagoService.procesarPago(request, correo));

        assertEquals("MONTO_INVALIDO", ex.getCodigo());
        verify(cuentaRepository, never()).debitarSaldo(any(), any());
    }

    @Test
    @DisplayName("Transacción duplicada previa: debe arrojar sendTx_duplicated antes de debitar")
    void testProcesarPago_TransaccionDuplicada_Previa() {
        PagoServicioRequest request = new PagoServicioRequest(
                numCuenta, "Password123", "1", "11", "CFE123456789", new BigDecimal("100.00")
        );

        when(usuarioRepository.findByCorreoIgnoreCase(correo)).thenReturn(Optional.of(usuarioMock));
        when(passwordEncoder.matches("Password123", usuarioMock.getPassword())).thenReturn(true);
        when(cuentaRepository.findByNumeroCuenta(numCuenta)).thenReturn(Optional.of(cuentaMock));
        when(productoRepository.findByIdProducto(11)).thenReturn(Optional.of(productoServicioMock));

        when(pagoRepository.existsByCuenta_IdBancariaAndIdProductoAndReferenciaAndMontoAndEstadoInAndCreadoEnAfter(
                eq(cuentaMock.getIdBancaria()), eq("11"), eq("CFE123456789"), eq(new BigDecimal("100.00")), any(), any()
        )).thenReturn(true);

        BusinessConflictException ex = assertThrows(BusinessConflictException.class, () ->
                pagoService.procesarPago(request, correo));

        assertEquals("sendTx_duplicated", ex.getCodigo());
        assertTrue(ex.getMessage().contains("espera unos minutos"));
        verify(cuentaRepository, never()).debitarSaldo(any(), any());
    }

    @Test
    @DisplayName("Carrier reporta duplicado: debe arrojar sendTx_duplicated y reembolsar saldo")
    void testProcesarPago_CarrierReportaDuplicado_RollbackYSendTxDuplicated() {
        PagoServicioRequest request = new PagoServicioRequest(
                numCuenta, "Password123", "1", "11", "CFE123456789", new BigDecimal("100.00")
        );

        when(usuarioRepository.findByCorreoIgnoreCase(correo)).thenReturn(Optional.of(usuarioMock));
        when(passwordEncoder.matches("Password123", usuarioMock.getPassword())).thenReturn(true);
        when(cuentaRepository.findByNumeroCuenta(numCuenta)).thenReturn(Optional.of(cuentaMock));
        when(productoRepository.findByIdProducto(11)).thenReturn(Optional.of(productoServicioMock));
        when(cuentaRepository.debitarSaldo(eq(cuentaMock.getIdBancaria()), eq(new BigDecimal("100.00")))).thenReturn(1);
        when(cuentaRepository.findById(cuentaMock.getIdBancaria())).thenReturn(Optional.of(cuentaMock));
        when(pagoRepository.saveAndFlush(any(PagoServicioEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        GestoPagoTxResponse rechazoDuplicado = new GestoPagoTxResponse();
        rechazoDuplicado.setCodigo("03");
        rechazoDuplicado.setTexto("sendTx_duplicated: Transaccion previamente procesada");
        when(txCommunicationService.ejecutarTransaccion(anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(rechazoDuplicado);

        BusinessConflictException ex = assertThrows(BusinessConflictException.class, () ->
                pagoService.procesarPago(request, correo));

        assertEquals("sendTx_duplicated", ex.getCodigo());
        assertTrue(ex.getMessage().contains("transacción duplicada"));
        verify(cuentaRepository).abonarSaldo(cuentaMock.getIdBancaria(), new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Validación cruzada: debe rechazar si el idServicio enviado no corresponde al idProducto del catálogo")
    void testProcesarPago_ServicioNoCoincideConProducto() {
        PagoServicioRequest request = new PagoServicioRequest(
                numCuenta, "Password123", "999", "11", "CFE123456789", new BigDecimal("100.00")
        );

        when(usuarioRepository.findByCorreoIgnoreCase(correo)).thenReturn(Optional.of(usuarioMock));
        when(passwordEncoder.matches("Password123", usuarioMock.getPassword())).thenReturn(true);
        when(cuentaRepository.findByNumeroCuenta(numCuenta)).thenReturn(Optional.of(cuentaMock));
        when(productoRepository.findByIdProducto(11)).thenReturn(Optional.of(productoServicioMock));

        BusinessConflictException ex = assertThrows(BusinessConflictException.class, () ->
                pagoService.procesarPago(request, correo));

        assertEquals("SERVICIO_NO_COINCIDE", ex.getCodigo());
        assertTrue(ex.getMessage().contains("no pertenece a la compañía o servicio indicado"));
        verify(cuentaRepository, never()).debitarSaldo(any(), any());
    }

    @Test
    @DisplayName("Conciliación explícita EXITOSA: confirma pago en PENDIENTE_CONCILIACION sin mover saldo")
    void testConciliarPago_ExplicitoExitoso() {
        PagoServicioEntity pagoPendiente = new PagoServicioEntity();
        pagoPendiente.setIdPago(UUID.randomUUID());
        pagoPendiente.setCuenta(cuentaMock);
        pagoPendiente.setIdServicio("1");
        pagoPendiente.setIdProducto("11");
        pagoPendiente.setNombreServicio("CFE");
        pagoPendiente.setNombreProducto("CFE Suministrador");
        pagoPendiente.setReferencia("CFE123456789");
        pagoPendiente.setMonto(new BigDecimal("100.00"));
        pagoPendiente.setUpc("UPC-CONC-01");
        pagoPendiente.setEstado(EstadoPagoServicio.PENDIENTE_CONCILIACION);

        when(pagoRepository.findByUpc("UPC-CONC-01")).thenReturn(Optional.of(pagoPendiente));
        when(pagoRepository.save(any(PagoServicioEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cuentaRepository.findById(cuentaMock.getIdBancaria())).thenReturn(Optional.of(cuentaMock));

        PagoConciliacionRequest request = new PagoConciliacionRequest(
                "UPC-CONC-01", true, "GP-777", "AUTH-777", "Confirmado en portal GestoPago"
        );

        PagoServicioResponse response = pagoService.conciliarPago(request, correo, false);

        assertEquals(EstadoPagoServicio.EXITOSO, response.estado());
        assertEquals("GP-777", response.folioTransaccionGp());
        assertEquals("AUTH-777", response.folioAutorizacion());
        verify(cuentaRepository, never()).abonarSaldo(any(), any());
    }

    @Test
    @DisplayName("Conciliación explícita REVERTIDA: reintegra el saldo atómicamente y cambia estado a REVERTIDO")
    void testConciliarPago_ExplicitoRevertido() {
        PagoServicioEntity pagoPendiente = new PagoServicioEntity();
        pagoPendiente.setIdPago(UUID.randomUUID());
        pagoPendiente.setCuenta(cuentaMock);
        pagoPendiente.setIdServicio("1");
        pagoPendiente.setIdProducto("11");
        pagoPendiente.setNombreServicio("CFE");
        pagoPendiente.setNombreProducto("CFE Suministrador");
        pagoPendiente.setReferencia("CFE123456789");
        pagoPendiente.setMonto(new BigDecimal("100.00"));
        pagoPendiente.setUpc("UPC-CONC-02");
        pagoPendiente.setEstado(EstadoPagoServicio.PENDIENTE_CONCILIACION);

        when(pagoRepository.findByUpc("UPC-CONC-02")).thenReturn(Optional.of(pagoPendiente));
        when(pagoRepository.save(any(PagoServicioEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cuentaRepository.findById(cuentaMock.getIdBancaria())).thenReturn(Optional.of(cuentaMock));

        PagoConciliacionRequest request = new PagoConciliacionRequest(
                "UPC-CONC-02", false, null, null, "No procesada en GestoPago"
        );

        PagoServicioResponse response = pagoService.conciliarPago(request, correo, false);

        assertEquals(EstadoPagoServicio.REVERTIDO, response.estado());
        verify(cuentaRepository).abonarSaldo(cuentaMock.getIdBancaria(), new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Conciliación automática idempotente: si el proveedor indica sendTx_duplicated (03), confirma EXITOSO sin reembolso")
    void testConciliarPago_IdempotenciaAutomaticaConfirmaExitoso() {
        PagoServicioEntity pagoPendiente = new PagoServicioEntity();
        pagoPendiente.setIdPago(UUID.randomUUID());
        pagoPendiente.setCuenta(cuentaMock);
        pagoPendiente.setIdServicio("1");
        pagoPendiente.setIdProducto("11");
        pagoPendiente.setNombreServicio("CFE");
        pagoPendiente.setNombreProducto("CFE Suministrador");
        pagoPendiente.setReferencia("CFE123456789");
        pagoPendiente.setMonto(new BigDecimal("100.00"));
        pagoPendiente.setUpc("UPC-CONC-03");
        pagoPendiente.setEstado(EstadoPagoServicio.PENDIENTE_CONCILIACION);

        when(pagoRepository.findByUpc("UPC-CONC-03")).thenReturn(Optional.of(pagoPendiente));
        when(pagoRepository.save(any(PagoServicioEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cuentaRepository.findById(cuentaMock.getIdBancaria())).thenReturn(Optional.of(cuentaMock));

        GestoPagoTxResponse gpDuplicado = new GestoPagoTxResponse();
        gpDuplicado.setCodigo("03");
        gpDuplicado.setTexto("sendTx_duplicated: UPC ya procesado");
        when(txCommunicationService.ejecutarTransaccion("1", "11", "CFE123456789", new BigDecimal("100.00"), "UPC-CONC-03"))
                .thenReturn(gpDuplicado);

        PagoConciliacionRequest request = new PagoConciliacionRequest("UPC-CONC-03", null, null, null, null);

        PagoServicioResponse response = pagoService.conciliarPago(request, correo, false);

        assertEquals(EstadoPagoServicio.EXITOSO, response.estado());
        verify(cuentaRepository, never()).abonarSaldo(any(), any());
    }
}
