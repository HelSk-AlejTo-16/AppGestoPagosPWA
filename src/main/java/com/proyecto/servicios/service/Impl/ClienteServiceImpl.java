package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.cliente.Cliente;
import com.proyecto.servicios.entity.cliente.Contacto;
import com.proyecto.servicios.entity.cliente.CuentaBancaria;
import com.proyecto.servicios.entity.cliente.Direccion;
import com.proyecto.servicios.entity.cliente.InformacionLaboral;
import com.proyecto.servicios.entity.cliente.Usuario;
import com.proyecto.servicios.exception.BusinessConflictException;
import com.proyecto.servicios.exception.ErrorValidacionException;
import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.model.cliente.ClienteActualizarRequest;
import com.proyecto.servicios.model.cliente.ClienteRegistroRequest;
import com.proyecto.servicios.model.cliente.ClienteResponse;
import com.proyecto.servicios.model.cliente.CuentaResponse;
import com.proyecto.servicios.model.cliente.DireccionRequest;
import com.proyecto.servicios.model.cliente.DireccionResponse;
import com.proyecto.servicios.model.cliente.InformacionLaboralRequest;
import com.proyecto.servicios.model.cliente.InformacionLaboralResponse;
import com.proyecto.servicios.model.cliente.ValidacionesIdentificadores;
import com.proyecto.servicios.model.cliente.PaginaResponse;
import com.proyecto.servicios.model.cliente.PasswordPolicy;
import com.proyecto.servicios.repositorys.cliente.ClienteRepository;
import com.proyecto.servicios.repositorys.cliente.CuentaBancariaRepository;
import com.proyecto.servicios.repositorys.cliente.UsuarioRepository;
import com.proyecto.servicios.service.ClienteService;
import com.proyecto.servicios.service.ClabeUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ClienteServiceImpl implements ClienteService {

    private static final int MAX_CUENTA_GENERATION_ATTEMPTS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClienteRepository clienteRepository;
    private final CuentaBancariaRepository cuentaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${bank.account.initial-balance:0.00}")
    private BigDecimal initialBalance;

    @Value("${bank.clabe.institution-code:}")
    private String clabeInstitutionCode;

    @Value("${bank.clabe.plaza-code:}")
    private String clabePlazaCode;

    public ClienteServiceImpl(ClienteRepository clienteRepository,
                              CuentaBancariaRepository cuentaRepository,
                              UsuarioRepository usuarioRepository,
                              PasswordEncoder passwordEncoder) {
        this.clienteRepository = clienteRepository;
        this.cuentaRepository = cuentaRepository;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public ClienteResponse registrar(ClienteRegistroRequest request) {
        validarMayorEdad(request.getFechaNacimiento());
        validarLongitudBcrypt(request.getPassword());

        String curp = normalizar(request.getCurp());
        String rfc = normalizar(request.getRfc());
        String correo = normalizarCorreo(request.getCorreo());
        validarIdentificadoresUnicos(curp, rfc);
        validarCorreoUnico(correo);
        BigDecimal saldoInicial = obtenerSaldoInicial();

        Cliente cliente = new Cliente();
        cliente.setNombre(limpiar(request.getNombre()));
        cliente.setSegundoNombre(limpiarOpcional(request.getSegundoNombre()));
        cliente.setApellidoPaterno(limpiar(request.getApellidoPaterno()));
        cliente.setApellidoMaterno(limpiarOpcional(request.getApellidoMaterno()));
        cliente.setFechaNacimiento(request.getFechaNacimiento());
        cliente.setSexo(request.getSexo());
        cliente.setEstadoCivil(request.getEstadoCivil());
        cliente.setCurp(curp);
        cliente.setRfc(rfc);
        cliente.setNacionalidad(limpiar(request.getNacionalidad()));

        Contacto contacto = new Contacto();
        contacto.setTelefonoMovil(request.getTelefonoMovil());
        contacto.setTelefonoAlternativo(limpiarOpcional(request.getTelefonoAlternativo()));
        cliente.setContacto(contacto);

        cliente.setDireccion(crearDireccion(request.getDireccion()));
        cliente.setInformacionLaboral(crearInformacionLaboral(request.getInformacionLaboral()));

        Usuario usuario = new Usuario();
        usuario.setCorreo(correo);
        usuario.setPassword(passwordEncoder.encode(request.getPassword()));
        usuario.setActivo(true);
        cliente.setUsuario(usuario);

        CuentaBancaria cuenta = new CuentaBancaria();
        cuenta.setActiva(true);
        cuenta.setSaldo(saldoInicial);
        cuenta.setNumeroCuenta(generarNumeroCuenta());
        cuenta.setClabe(generarClabe());
        cliente.addCuenta(cuenta);

        return toResponse(clienteRepository.saveAndFlush(cliente));
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<ClienteResponse> obtenerTodos(int pagina, int tamano) {
        return paginar(clienteRepository.findAllByOrderByCreadoEnDesc(crearPagina(pagina, tamano)));
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<ClienteResponse> obtenerActivos(int pagina, int tamano) {
        return paginar(clienteRepository.findAllByActivoTrueOrderByCreadoEnDesc(crearPagina(pagina, tamano)));
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteResponse obtenerPorId(UUID id) {
        return toResponse(buscarPorId(id));
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteResponse obtenerPorIdPropio(UUID id, String correoAutenticado) {
        Cliente cliente = buscarPorId(id);
        validarPropietario(cliente, correoAutenticado);
        return toResponse(cliente);
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteResponse obtenerPorCurp(String curp) {
        return clienteRepository.findWithRelationsByCurpIgnoreCase(normalizar(curp))
                .map(this::toResponse)
                .orElseThrow(() -> clienteNoEncontrado());
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteResponse obtenerPorRfc(String rfc) {
        return clienteRepository.findWithRelationsByRfcIgnoreCase(normalizar(rfc))
                .map(this::toResponse)
                .orElseThrow(() -> clienteNoEncontrado());
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteResponse obtenerPorCorreo(String correo) {
        return clienteRepository.findWithRelationsByUsuarioCorreoIgnoreCase(normalizarCorreo(correo))
                .map(this::toResponse)
                .orElseThrow(() -> clienteNoEncontrado());
    }

    @Override
    @Transactional(readOnly = true)
    public ClienteResponse obtenerPorNumeroCuenta(String numeroCuenta) {
        return cuentaRepository.findByNumeroCuenta(numeroCuenta)
                .map(CuentaBancaria::getCliente)
                .map(this::toResponse)
                .orElseThrow(() -> clienteNoEncontrado());
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<ClienteResponse> obtenerRegistrados(
            LocalDate desde, LocalDate hasta, int pagina, int tamano) {
        if (desde == null || hasta == null || desde.isAfter(hasta)) {
            throw new ErrorValidacionException("El rango de fechas es inválido.");
        }
        OffsetDateTime inicio = desde.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime finExclusivo = hasta.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        return paginar(clienteRepository.findAllByCreadoEnGreaterThanEqualAndCreadoEnLessThanOrderByCreadoEnDesc(
                inicio, finExclusivo, crearPagina(pagina, tamano)
        ));
    }

    @Override
    public ClienteResponse actualizar(UUID id, String correoAutenticado, ClienteActualizarRequest request) {
        Cliente cliente = buscarPorId(id);
        validarPropietario(cliente, correoAutenticado);
        validarUsuarioActivo(cliente);
        validarMayorEdad(request.getFechaNacimiento());
        if (!ValidacionesIdentificadores.curpConsistente(cliente.getCurp(), request.getFechaNacimiento(), request.getSexo())
                || !ValidacionesIdentificadores.rfcConsistente(cliente.getRfc(), request.getFechaNacimiento())) {
            throw new ErrorValidacionException(
                    "La fecha de nacimiento y el sexo deben seguir siendo compatibles con la CURP y el RFC registrados."
            );
        }
        String correo = normalizarCorreo(request.getCorreo());
        if (!normalizarCorreo(cliente.getUsuario().getCorreo()).equals(correo)
                && usuarioRepository.existsByCorreoIgnoreCase(correo)) {
            throw correoDuplicado();
        }

        cliente.setNombre(limpiar(request.getNombre()));
        cliente.setSegundoNombre(limpiarOpcional(request.getSegundoNombre()));
        cliente.setApellidoPaterno(limpiar(request.getApellidoPaterno()));
        cliente.setApellidoMaterno(limpiarOpcional(request.getApellidoMaterno()));
        cliente.setFechaNacimiento(request.getFechaNacimiento());
        cliente.setSexo(request.getSexo());
        cliente.setEstadoCivil(request.getEstadoCivil());
        cliente.setNacionalidad(limpiar(request.getNacionalidad()));
        cliente.getUsuario().setCorreo(correo);
        cliente.getContacto().setTelefonoMovil(request.getTelefonoMovil());
        cliente.getContacto().setTelefonoAlternativo(limpiarOpcional(request.getTelefonoAlternativo()));
        actualizarDireccion(cliente.getDireccion(), request.getDireccion());
        actualizarInformacionLaboral(cliente.getInformacionLaboral(), request.getInformacionLaboral());

        return toResponse(clienteRepository.saveAndFlush(cliente));
    }

    @Override
    public void desactivar(UUID id, String correoAutenticado) {
        Cliente cliente = buscarPorId(id);
        validarPropietario(cliente, correoAutenticado);
        validarUsuarioActivo(cliente);
        cliente.setActivo(false);
        if (cliente.getUsuario() != null) {
            cliente.getUsuario().setActivo(false);
        }
        cliente.getCuentas().forEach(cuenta -> cuenta.setActiva(false));
        clienteRepository.save(cliente);
    }

    private void validarPropietario(Cliente cliente, String correoAutenticado) {
        if (cliente.getUsuario() == null
                || !normalizarCorreo(cliente.getUsuario().getCorreo())
                .equals(normalizarCorreo(correoAutenticado))) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Solo puede consultar o modificar sus propios datos.");
        }
    }

    private void validarUsuarioActivo(Cliente cliente) {
        if (!cliente.isActivo() || !cliente.getUsuario().isActivo()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Un usuario inactivo no puede modificar los datos del cliente.");
        }
    }

    @Override
    public ClienteResponse reactivar(UUID id) {
        Cliente cliente = buscarPorId(id);
        if (cliente.isActivo()) {
            return toResponse(cliente);
        }

        cliente.setActivo(true);
        clienteRepository.saveAndFlush(cliente);
        if (cliente.getUsuario() != null) {
            cliente.getUsuario().setActivo(true);
            usuarioRepository.save(cliente.getUsuario());
        }
        cliente.getCuentas().forEach(cuenta -> cuenta.setActiva(true));
        cuentaRepository.saveAllAndFlush(cliente.getCuentas());
        return toResponse(cliente);
    }

    private Pageable crearPagina(int pagina, int tamano) {
        if (pagina < 0 || tamano < 1 || tamano > 100) {
            throw new ErrorValidacionException("La página debe ser >= 0 y el tamaño debe estar entre 1 y 100.");
        }
        return PageRequest.of(pagina, tamano, Sort.by(Sort.Direction.DESC, "creadoEn"));
    }

    private PaginaResponse<ClienteResponse> paginar(Page<Cliente> clientes) {
        List<UUID> clienteIds = clientes.getContent().stream().map(Cliente::getIdPersona).toList();
        Map<UUID, List<CuentaBancaria>> cuentasPorCliente = clienteIds.isEmpty()
                ? Map.of()
                : cuentaRepository.findAllByCliente_IdPersonaIn(clienteIds).stream()
                        .collect(Collectors.groupingBy(cuenta -> cuenta.getCliente().getIdPersona()));
        return PaginaResponse.desde(clientes.map(cliente -> toResponse(
                cliente, cuentasPorCliente.getOrDefault(cliente.getIdPersona(), List.of())
        )));
    }

    private Cliente buscarPorId(UUID id) {
        return clienteRepository.findWithRelationsByIdPersona(id)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "CLIENTE_NO_ENCONTRADO", "No se encontró el cliente solicitado."));
    }

    private void validarIdentificadoresUnicos(String curp, String rfc) {
        if (clienteRepository.existsByCurpIgnoreCase(curp)) {
            throw new BusinessConflictException("CURP_DUPLICADA",
                    "Ya existe un cliente registrado con esa CURP.");
        }
        if (clienteRepository.existsByRfcIgnoreCase(rfc)) {
            throw new BusinessConflictException("RFC_DUPLICADO",
                    "Ya existe un cliente registrado con ese RFC.");
        }
    }

    private void validarCorreoUnico(String correo) {
        if (usuarioRepository.existsByCorreoIgnoreCase(correo)) {
            throw correoDuplicado();
        }
    }

    private BusinessConflictException correoDuplicado() {
        return new BusinessConflictException("CORREO_DUPLICADO",
                "Ya existe un usuario registrado con ese correo electrónico.");
    }

    private RecursoNoEncontradoException clienteNoEncontrado() {
        return new RecursoNoEncontradoException("CLIENTE_NO_ENCONTRADO",
                "No se encontró el cliente solicitado.");
    }

    private void validarMayorEdad(LocalDate fechaNacimiento) {
        if (fechaNacimiento == null || fechaNacimiento.isAfter(LocalDate.now().minusYears(18))) {
            throw new ErrorValidacionException("El cliente debe tener al menos 18 años.");
        }
    }

    private BigDecimal obtenerSaldoInicial() {
        if (initialBalance == null || initialBalance.signum() < 0) {
            throw new IllegalStateException("El saldo inicial configurado no puede ser negativo.");
        }
        BigDecimal saldo = initialBalance.setScale(2, RoundingMode.HALF_UP);
        if (saldo.precision() > 15) {
            throw new IllegalStateException("El saldo inicial configurado excede la precisión permitida.");
        }
        return saldo;
    }

    private void validarLongitudBcrypt(String password) {
        if (!PasswordPolicy.compatibleConBcrypt(password)) {
            throw new ErrorValidacionException("La contraseña no puede superar 72 bytes en UTF-8 con BCrypt.");
        }
    }

    private String generarNumeroCuenta() {
        for (int intento = 0; intento < MAX_CUENTA_GENERATION_ATTEMPTS; intento++) {
            String numero = digitosAleatorios(20);
            if (!cuentaRepository.existsByNumeroCuenta(numero)) {
                return numero;
            }
        }
        throw new IllegalStateException("No fue posible generar un número de cuenta único.");
    }

    private String generarClabe() {
        if (clabeInstitutionCode == null || !clabeInstitutionCode.matches("\\d{3}")) {
            throw new IllegalStateException(
                    "Configure bank.clabe.institution-code con el código de institución CLABE de 3 dígitos."
            );
        }
        if (clabePlazaCode == null || !clabePlazaCode.matches("\\d{3}")) {
            throw new IllegalStateException(
                    "Configure bank.clabe.plaza-code con el código de plaza CLABE de 3 dígitos."
            );
        }
        for (int intento = 0; intento < MAX_CUENTA_GENERATION_ATTEMPTS; intento++) {
            String primerosDiecisiete = clabeInstitutionCode + clabePlazaCode + digitosAleatorios(11);
            String clabe = ClabeUtils.agregarDigitoVerificador(primerosDiecisiete);
            if (!cuentaRepository.existsByClabe(clabe)) {
                return clabe;
            }
        }
        throw new IllegalStateException("No fue posible generar una CLABE única.");
    }

    private String digitosAleatorios(int longitud) {
        StringBuilder valor = new StringBuilder(longitud);
        for (int i = 0; i < longitud; i++) {
            valor.append(RANDOM.nextInt(10));
        }
        return valor.toString();
    }

    private Direccion crearDireccion(DireccionRequest request) {
        Direccion direccion = new Direccion();
        actualizarDireccion(direccion, request);
        return direccion;
    }

    private void actualizarDireccion(Direccion direccion, DireccionRequest request) {
        direccion.setCalle(limpiar(request.getCalle()));
        direccion.setNumeroExterior(limpiar(request.getNumeroExterior()));
        direccion.setNumeroInterior(limpiarOpcional(request.getNumeroInterior()));
        direccion.setColonia(limpiar(request.getColonia()));
        direccion.setMunicipio(limpiar(request.getMunicipio()));
        direccion.setEstado(limpiar(request.getEstado()));
        direccion.setCodigoPostal(request.getCodigoPostal());
        direccion.setPais(limpiar(request.getPais()));
    }

    private InformacionLaboral crearInformacionLaboral(InformacionLaboralRequest request) {
        InformacionLaboral informacion = new InformacionLaboral();
        actualizarInformacionLaboral(informacion, request);
        return informacion;
    }

    private void actualizarInformacionLaboral(InformacionLaboral informacion, InformacionLaboralRequest request) {
        informacion.setIngresoMensual(request.getIngresoMensual());
        informacion.setOcupacion(limpiar(request.getOcupacion()));
        informacion.setEmpresa(limpiar(request.getEmpresa()));
    }

    private ClienteResponse toResponse(Cliente cliente) {
        return toResponse(cliente, cliente.getCuentas());
    }

    private ClienteResponse toResponse(Cliente cliente, List<CuentaBancaria> cuentasCliente) {
        Direccion direccion = cliente.getDireccion();
        InformacionLaboral laboral = cliente.getInformacionLaboral();
        List<CuentaResponse> cuentas = cuentasCliente.stream().map(this::toResponse).toList();
        return new ClienteResponse(
                cliente.getIdPersona(),
                cliente.getCreadoEn(),
                cliente.isActivo(),
                cliente.getNombre(),
                cliente.getSegundoNombre(),
                cliente.getApellidoPaterno(),
                cliente.getApellidoMaterno(),
                cliente.getFechaNacimiento(),
                cliente.getSexo(),
                cliente.getEstadoCivil(),
                cliente.getCurp(),
                cliente.getRfc(),
                cliente.getNacionalidad(),
                cliente.getUsuario().getCorreo(),
                cliente.getContacto().getTelefonoMovil(),
                cliente.getContacto().getTelefonoAlternativo(),
                new DireccionResponse(
                        direccion.getCalle(),
                        direccion.getNumeroExterior(),
                        direccion.getNumeroInterior(),
                        direccion.getColonia(),
                        direccion.getMunicipio(),
                        direccion.getEstado(),
                        direccion.getCodigoPostal(),
                        direccion.getPais()
                ),
                new InformacionLaboralResponse(
                        laboral.getIngresoMensual(),
                        laboral.getOcupacion(),
                        laboral.getEmpresa()
                ),
                cuentas
        );
    }

    private CuentaResponse toResponse(CuentaBancaria cuenta) {
        return new CuentaResponse(
                cuenta.getIdBancaria(),
                cuenta.getCliente().getIdPersona(),
                cuenta.getNumeroCuenta(),
                cuenta.getClabe(),
                cuenta.getSaldo(),
                cuenta.isActiva(),
                cuenta.getCreadoEn()
        );
    }

    private String normalizar(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizarCorreo(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String limpiar(String value) {
        return value == null ? null : value.trim();
    }

    private String limpiarOpcional(String value) {
        String limpio = limpiar(value);
        return limpio == null || limpio.isBlank() ? null : limpio;
    }
}
