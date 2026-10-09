package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.cliente.Cliente;
import com.proyecto.servicios.entity.cliente.Contacto;
import com.proyecto.servicios.entity.cliente.CuentaBancaria;
import com.proyecto.servicios.entity.cliente.Direccion;
import com.proyecto.servicios.entity.cliente.InformacionLaboral;
import com.proyecto.servicios.entity.cliente.Usuario;
import com.proyecto.servicios.exception.BusinessConflictException;
import com.proyecto.servicios.exception.ErrorValidacionException;
import com.proyecto.servicios.model.cliente.ClienteRegistroRequest;
import com.proyecto.servicios.model.cliente.ClienteActualizarRequest;
import com.proyecto.servicios.model.cliente.DireccionRequest;
import com.proyecto.servicios.model.cliente.InformacionLaboralRequest;
import com.proyecto.servicios.repositorys.cliente.ClienteRepository;
import com.proyecto.servicios.repositorys.cliente.CuentaBancariaRepository;
import com.proyecto.servicios.repositorys.cliente.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteServiceImplTest {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private CuentaBancariaRepository cuentaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private ClienteServiceImpl clienteService;

    @BeforeEach
    void configurarSaldoInicial() {
        ReflectionTestUtils.setField(clienteService, "initialBalance", new BigDecimal("125.50"));
        ReflectionTestUtils.setField(clienteService, "clabeInstitutionCode", "012");
        ReflectionTestUtils.setField(clienteService, "clabePlazaCode", "001");
    }

    @Test
    void registrar_creaClienteCuentaYUsuarioConPasswordCifrado() {
        ClienteRegistroRequest request = requestValido();
        when(clienteRepository.existsByCurpIgnoreCase("LODA900101MDFPRN05")).thenReturn(false);
        when(clienteRepository.existsByRfcIgnoreCase("LODA900101ABC")).thenReturn(false);
        when(usuarioRepository.existsByCorreoIgnoreCase("cliente@example.com")).thenReturn(false);
        when(cuentaRepository.existsByNumeroCuenta(any())).thenReturn(false);
        when(cuentaRepository.existsByClabe(any())).thenReturn(false);
        when(passwordEncoder.encode("Secreta1!")).thenReturn("bcrypt-hash");
        when(clienteRepository.saveAndFlush(any(Cliente.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        clienteService.registrar(request);

        ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);
        verify(clienteRepository).saveAndFlush(captor.capture());
        Cliente guardado = captor.getValue();
        Usuario usuario = guardado.getUsuario();
        CuentaBancaria cuenta = guardado.getCuentas().get(0);

        assertEquals("bcrypt-hash", usuario.getPassword());
        assertEquals("cliente@example.com", usuario.getCorreo());
        assertTrue(usuario.isActivo());
        assertTrue(guardado.isActivo());
        assertTrue(cuenta.isActiva());
        assertEquals(new BigDecimal("125.50"), cuenta.getSaldo());
        assertEquals(20, cuenta.getNumeroCuenta().length());
        assertEquals(18, cuenta.getClabe().length());
        assertEquals(guardado, cuenta.getCliente());
        assertFalse(usuario.getPassword().contains(request.getPassword()));
    }

    @Test
    void registrar_rechazaClienteMenorDeEdad() {
        ClienteRegistroRequest request = requestValido();
        request.setFechaNacimiento(LocalDate.now().minusYears(18).plusDays(1));

        assertThrows(ErrorValidacionException.class, () -> clienteService.registrar(request));
        verify(clienteRepository, never()).saveAndFlush(any());
    }

    @Test
    void registrar_rechazaCurpDuplicada() {
        when(clienteRepository.existsByCurpIgnoreCase("LODA900101MDFPRN05")).thenReturn(true);

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class, () -> clienteService.registrar(requestValido()));
        assertEquals("CURP_DUPLICADA", exception.getCodigo());
        verify(clienteRepository, never()).saveAndFlush(any());
    }

    @Test
    void registrar_rechazaCorreoExistenteSinImportarMayusculas() {
        when(clienteRepository.existsByCurpIgnoreCase("LODA900101MDFPRN05")).thenReturn(false);
        when(clienteRepository.existsByRfcIgnoreCase("LODA900101ABC")).thenReturn(false);
        when(usuarioRepository.existsByCorreoIgnoreCase("cliente@example.com")).thenReturn(true);

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> {
                    ClienteRegistroRequest request = requestValido();
                    request.setCorreo("CLIENTE@EXAMPLE.COM");
                    clienteService.registrar(request);
                }
        );

        assertEquals("CORREO_DUPLICADO", exception.getCodigo());
        verify(clienteRepository, never()).saveAndFlush(any());
    }

    @Test
    void actualizar_rechazaModificarDatosDeOtroUsuario() {
        UUID id = UUID.randomUUID();
        Cliente cliente = clienteConUsuario("owner@example.com", true, true);
        when(clienteRepository.findWithRelationsByIdPersona(id)).thenReturn(Optional.of(cliente));

        assertThrows(AccessDeniedException.class,
                () -> clienteService.actualizar(id, "caller@example.com", new ClienteActualizarRequest()));
        verify(clienteRepository, never()).saveAndFlush(any());
    }

    @Test
    void actualizar_rechazaUsuarioInactivoAunqueSeaElPropietario() {
        UUID id = UUID.randomUUID();
        Cliente cliente = clienteConUsuario("owner@example.com", true, false);
        when(clienteRepository.findWithRelationsByIdPersona(id)).thenReturn(Optional.of(cliente));

        assertThrows(AccessDeniedException.class,
                () -> clienteService.actualizar(id, "owner@example.com", new ClienteActualizarRequest()));
        verify(clienteRepository, never()).saveAndFlush(any());
    }

    @Test
    void obtenerPorIdPropio_rechazaConsultarPerfilDeOtroCliente() {
        UUID id = UUID.randomUUID();
        Cliente cliente = clienteConUsuario("owner@example.com", true, true);
        when(clienteRepository.findWithRelationsByIdPersona(id)).thenReturn(Optional.of(cliente));

        assertThrows(AccessDeniedException.class,
                () -> clienteService.obtenerPorIdPropio(id, "caller@example.com"));
    }

    private Cliente clienteConUsuario(String correo, boolean clienteActivo, boolean usuarioActivo) {
        Cliente cliente = new Cliente();
        cliente.setIdPersona(UUID.randomUUID());
        cliente.setActivo(clienteActivo);
        Usuario usuario = new Usuario();
        usuario.setCorreo(correo);
        usuario.setActivo(usuarioActivo);
        cliente.setUsuario(usuario);
        return cliente;
    }

    @Test
    void registrar_rechazaPasswordQueSupera72BytesEnUtf8() {
        ClienteRegistroRequest request = requestValido();
        request.setPassword("é".repeat(37));

        assertThrows(ErrorValidacionException.class, () -> clienteService.registrar(request));
        verify(clienteRepository, never()).saveAndFlush(any());
    }

    @Test
    void reactivar_activaClienteUsuarioYCuentas() {
        UUID id = UUID.randomUUID();
        Cliente cliente = clienteParaReactivacion(id);
        when(clienteRepository.findWithRelationsByIdPersona(id)).thenReturn(Optional.of(cliente));
        when(clienteRepository.saveAndFlush(cliente)).thenReturn(cliente);

        clienteService.reactivar(id);

        assertTrue(cliente.isActivo());
        assertTrue(cliente.getUsuario().isActivo());
        assertTrue(cliente.getCuentas().get(0).isActiva());
        verify(usuarioRepository).save(cliente.getUsuario());
        verify(cuentaRepository).saveAllAndFlush(cliente.getCuentas());
    }

    @Test
    void obtenerTodos_rechazaTamanoSuperiorAlLimite() {
        assertThrows(ErrorValidacionException.class, () -> clienteService.obtenerTodos(0, 101));
    }

    private Cliente clienteParaReactivacion(UUID id) {
        Cliente cliente = new Cliente();
        cliente.setIdPersona(id);
        cliente.setActivo(false);
        cliente.setNombre("Ana");
        cliente.setApellidoPaterno("López");
        cliente.setApellidoMaterno("Díaz");
        cliente.setNacionalidad("Mexicana");

        Contacto contacto = new Contacto();
        contacto.setTelefonoMovil("5512345678");
        cliente.setContacto(contacto);
        cliente.setDireccion(new Direccion());
        cliente.setInformacionLaboral(new InformacionLaboral());

        Usuario usuario = new Usuario();
        usuario.setCorreo("cliente@example.com");
        usuario.setActivo(false);
        cliente.setUsuario(usuario);

        CuentaBancaria cuenta = new CuentaBancaria();
        cuenta.setNumeroCuenta("12345678901234567890");
        cuenta.setClabe("032180000118359719");
        cuenta.setSaldo(new BigDecimal("0.00"));
        cuenta.setActiva(false);
        cliente.addCuenta(cuenta);
        return cliente;
    }

    private ClienteRegistroRequest requestValido() {
        ClienteRegistroRequest request = new ClienteRegistroRequest();
        request.setNombre("Ana");
        request.setApellidoPaterno("López");
        request.setApellidoMaterno("Díaz");
        request.setFechaNacimiento(LocalDate.of(1990, 1, 1));
        request.setSexo(com.proyecto.servicios.entity.cliente.Sexo.F);
        request.setEstadoCivil(com.proyecto.servicios.entity.cliente.EstadoCivil.Soltero);
        request.setCurp("loda900101mdfprn05");
        request.setRfc("loda900101abc");
        request.setCorreo("Cliente@Example.com");
        request.setPassword("Secreta1!");
        request.setTelefonoMovil("5512345678");
        request.setNacionalidad("Mexicana");

        DireccionRequest direccion = new DireccionRequest();
        direccion.setCalle("Reforma");
        direccion.setNumeroExterior("100");
        direccion.setColonia("Centro");
        direccion.setMunicipio("Cuauhtémoc");
        direccion.setEstado("Ciudad de México");
        direccion.setCodigoPostal("06000");
        direccion.setPais("México");
        request.setDireccion(direccion);

        InformacionLaboralRequest laboral = new InformacionLaboralRequest();
        laboral.setIngresoMensual(new BigDecimal("25000.00"));
        laboral.setOcupacion("Analista");
        laboral.setEmpresa("Empresa");
        request.setInformacionLaboral(laboral);
        return request;
    }
}
