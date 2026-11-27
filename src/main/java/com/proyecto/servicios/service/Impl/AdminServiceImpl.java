package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.cliente.Cliente;
import com.proyecto.servicios.entity.cliente.Contacto;
import com.proyecto.servicios.entity.cliente.CuentaBancaria;
import com.proyecto.servicios.entity.cliente.Usuario;
import com.proyecto.servicios.entity.pagos.EstadoPagoServicio;
import com.proyecto.servicios.exception.BusinessConflictException;
import com.proyecto.servicios.exception.ErrorValidacionException;
import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.model.admin.AdminAbonoCuentaRequest;
import com.proyecto.servicios.model.admin.AdminResumenResponse;
import com.proyecto.servicios.model.admin.AdminUsuarioBusquedaRequest;
import com.proyecto.servicios.model.admin.AdminUsuarioItemResponse;
import com.proyecto.servicios.model.cliente.CuentaDepositoRequest;
import com.proyecto.servicios.model.cliente.CuentaResponse;
import com.proyecto.servicios.model.cliente.CuentaSaldoResponse;
import com.proyecto.servicios.model.cliente.PaginaResponse;
import com.proyecto.servicios.repositorys.cliente.ClienteRepository;
import com.proyecto.servicios.repositorys.cliente.CuentaBancariaRepository;
import com.proyecto.servicios.repositorys.cliente.UsuarioRepository;
import com.proyecto.servicios.repositorys.pagos.PagoServicioRepository;
import com.proyecto.servicios.service.AdminService;
import com.proyecto.servicios.service.CuentaService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminServiceImpl implements AdminService {

    private static final Set<String> ROLES_PERMITIDOS = Set.of("ROLE_USER", "ROLE_ADMIN");

    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final CuentaBancariaRepository cuentaBancariaRepository;
    private final PagoServicioRepository pagoServicioRepository;
    private final CuentaService cuentaService;

    @Override
    @Transactional(readOnly = true)
    public AdminResumenResponse obtenerResumen() {
        OffsetDateTime ahora = OffsetDateTime.now();
        long totalClientes = clienteRepository.count();
        long clientesActivos = clienteRepository.countByActivo(true);
        long clientesInactivos = clienteRepository.countByActivo(false);
        long usuariosBloqueados = usuarioRepository.countByFechaBloqueoAfter(ahora);
        long totalCuentas = cuentaBancariaRepository.count();
        BigDecimal saldoTotal = cuentaBancariaRepository.sumarSaldoTotal();
        if (saldoTotal == null) {
            saldoTotal = BigDecimal.ZERO;
        }
        long totalPagos = pagoServicioRepository.count();
        long pagosFallidos = pagoServicioRepository.countByEstadoIn(List.of(
                EstadoPagoServicio.RECHAZADO,
                EstadoPagoServicio.PENDIENTE_CONCILIACION,
                EstadoPagoServicio.REVERTIDO
        ));

        return new AdminResumenResponse(
                totalClientes,
                clientesActivos,
                clientesInactivos,
                usuariosBloqueados,
                totalCuentas,
                saldoTotal,
                totalPagos,
                pagosFallidos
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<AdminUsuarioItemResponse> buscarUsuarios(AdminUsuarioBusquedaRequest criterios) {
        AdminUsuarioBusquedaRequest filtro = criterios == null
                ? new AdminUsuarioBusquedaRequest(null, null, null, null, 0, 15)
                : criterios;

        OffsetDateTime ahora = OffsetDateTime.now();
        Specification<Cliente> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<Cliente, Usuario> usuarioJoin = root.join("usuario", JoinType.LEFT);
            Join<Cliente, Contacto> contactoJoin = root.join("contacto", JoinType.LEFT);

            if (filtro.busqueda() != null && !filtro.busqueda().isBlank()) {
                String[] terminos = filtro.busqueda().trim().toLowerCase(Locale.ROOT).split("\\s+");
                for (String termino : terminos) {
                    String patron = "%" + termino + "%";
                    predicates.add(cb.or(
                            cb.like(cb.lower(cb.coalesce(root.get("nombre"), "")), patron),
                            cb.like(cb.lower(cb.coalesce(root.get("segundoNombre"), "")), patron),
                            cb.like(cb.lower(cb.coalesce(root.get("apellidoPaterno"), "")), patron),
                            cb.like(cb.lower(cb.coalesce(root.get("apellidoMaterno"), "")), patron),
                            cb.like(cb.lower(cb.coalesce(root.get("curp"), "")), patron),
                            cb.like(cb.lower(cb.coalesce(root.get("rfc"), "")), patron),
                            cb.like(cb.lower(cb.coalesce(usuarioJoin.get("correo"), "")), patron),
                            cb.like(cb.lower(cb.coalesce(contactoJoin.get("telefonoMovil"), "")), patron)
                    ));
                }
            }

            if (filtro.activo() != null) {
                predicates.add(cb.equal(root.get("activo"), filtro.activo()));
            }

            if (filtro.rol() != null && !filtro.rol().isBlank()) {
                predicates.add(cb.equal(usuarioJoin.get("rol"), filtro.rol().trim()));
            }

            if (Boolean.TRUE.equals(filtro.soloBloqueados())) {
                predicates.add(cb.greaterThan(usuarioJoin.get("fechaBloqueo"), ahora));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Pageable pageable = PageRequest.of(
                filtro.pagina(),
                filtro.tamano(),
                Sort.by(Sort.Direction.DESC, "creadoEn")
        );

        Page<Cliente> page = clienteRepository.findAll(spec, pageable);
        List<UUID> clienteIds = page.getContent().stream()
                .map(Cliente::getIdPersona)
                .toList();

        Map<UUID, List<CuentaBancaria>> cuentasPorCliente = clienteIds.isEmpty()
                ? Collections.emptyMap()
                : cuentaBancariaRepository.findAllByCliente_IdPersonaIn(clienteIds).stream()
                        .collect(Collectors.groupingBy(c -> c.getCliente().getIdPersona()));

        List<AdminUsuarioItemResponse> items = page.getContent().stream()
                .map(cliente -> mapearUsuario(
                        cliente,
                        cuentasPorCliente.getOrDefault(cliente.getIdPersona(), List.of()),
                        ahora
                ))
                .toList();

        return PaginaResponse.desde(new PageImpl<>(items, pageable, page.getTotalElements()));
    }

    @Override
    @Transactional
    public AdminUsuarioItemResponse cambiarEstadoUsuario(UUID idCliente, boolean activo, String correoAdmin) {
        Cliente cliente = obtenerClienteConRelaciones(idCliente);
        Usuario usuario = cliente.getUsuario();

        if (!activo && usuario != null && correoAdmin != null
                && correoAdmin.equalsIgnoreCase(usuario.getCorreo())) {
            throw new BusinessConflictException(
                    "AUTO_DESACTIVACION_NO_PERMITIDA",
                    "No puedes desactivar tu propia cuenta administradora."
            );
        }

        cliente.setActivo(activo);
        if (usuario != null) {
            usuario.setActivo(activo);
            if (!activo) {
                limpiarSesionActiva(usuario);
            }
            usuarioRepository.save(usuario);
        }
        clienteRepository.save(cliente);

        log.info("Admin {} {} usuario {}", correoAdmin, activo ? "activó" : "desactivó", idCliente);
        return mapearUsuario(cliente, obtenerCuentasCliente(cliente), OffsetDateTime.now());
    }

    @Override
    @Transactional
    public AdminUsuarioItemResponse cambiarRolUsuario(UUID idCliente, String nuevoRol, String correoAdmin) {
        if (nuevoRol == null || !ROLES_PERMITIDOS.contains(nuevoRol.trim())) {
            throw new ErrorValidacionException("El rol especificado debe ser ROLE_USER o ROLE_ADMIN.");
        }
        String rolNormalizado = nuevoRol.trim();

        Cliente cliente = obtenerClienteConRelaciones(idCliente);
        Usuario usuario = obtenerUsuarioObligatorio(cliente);

        if (correoAdmin != null
                && correoAdmin.equalsIgnoreCase(usuario.getCorreo())
                && !"ROLE_ADMIN".equals(rolNormalizado)) {
            throw new BusinessConflictException(
                    "AUTO_CAMBIO_ROL_NO_PERMITIDO",
                    "No puedes remover tu propio rol de administrador."
            );
        }

        usuario.setRol(rolNormalizado);
        usuarioRepository.save(usuario);

        log.info("Admin {} cambió rol del cliente {} a {}", correoAdmin, idCliente, rolNormalizado);
        return mapearUsuario(cliente, obtenerCuentasCliente(cliente), OffsetDateTime.now());
    }

    @Override
    @Transactional
    public AdminUsuarioItemResponse desbloquearUsuario(UUID idCliente) {
        Cliente cliente = obtenerClienteConRelaciones(idCliente);
        Usuario usuario = obtenerUsuarioObligatorio(cliente);

        usuario.setIntentosFallidos(0);
        usuario.setFechaBloqueo(null);
        usuarioRepository.save(usuario);

        log.info("Usuario desbloqueado administrativamente: cliente {}", idCliente);
        return mapearUsuario(cliente, obtenerCuentasCliente(cliente), OffsetDateTime.now());
    }

    @Override
    @Transactional
    public AdminUsuarioItemResponse forzarCierreSesion(UUID idCliente) {
        Cliente cliente = obtenerClienteConRelaciones(idCliente);
        Usuario usuario = obtenerUsuarioObligatorio(cliente);

        limpiarSesionActiva(usuario);
        usuarioRepository.save(usuario);

        log.info("Sesión cerrada administrativamente para cliente {}", idCliente);
        return mapearUsuario(cliente, obtenerCuentasCliente(cliente), OffsetDateTime.now());
    }

    @Override
    @Transactional
    public CuentaSaldoResponse abonarSaldoACuenta(AdminAbonoCuentaRequest request, String correoAdmin) {
        if (request == null) {
            throw new ErrorValidacionException("Los datos del abono son obligatorios.");
        }
        String concepto = (request.concepto() == null || request.concepto().isBlank())
                ? "Abono administrativo"
                : request.concepto().trim();

        return cuentaService.depositar(
                new CuentaDepositoRequest(request.numeroCuenta(), request.monto(), concepto, null),
                correoAdmin,
                true
        );
    }

    private Cliente obtenerClienteConRelaciones(UUID idCliente) {
        if (idCliente == null) {
            throw new ErrorValidacionException("El identificador del cliente es obligatorio.");
        }
        return clienteRepository.findWithRelationsByIdPersona(idCliente)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "CLIENTE_NO_ENCONTRADO",
                        "No se encontró el cliente especificado."
                ));
    }

    private Usuario obtenerUsuarioObligatorio(Cliente cliente) {
        if (cliente.getUsuario() == null) {
            throw new RecursoNoEncontradoException(
                    "USUARIO_NO_ENCONTRADO",
                    "El cliente no tiene credenciales de usuario asociadas."
            );
        }
        return cliente.getUsuario();
    }

    private void limpiarSesionActiva(Usuario usuario) {
        usuario.setIdSesionActiva(null);
        usuario.setPlataformaActiva(null);
        usuario.setFechaInicioSesion(null);
    }

    private List<CuentaBancaria> obtenerCuentasCliente(Cliente cliente) {
        if (cliente.getCuentas() != null && !cliente.getCuentas().isEmpty()) {
            return cliente.getCuentas();
        }
        return cuentaBancariaRepository.findAllByCliente_IdPersonaIn(List.of(cliente.getIdPersona()));
    }

    private AdminUsuarioItemResponse mapearUsuario(
            Cliente cliente,
            List<CuentaBancaria> cuentas,
            OffsetDateTime ahora
    ) {
        Usuario usuario = cliente.getUsuario();
        Contacto contacto = cliente.getContacto();

        String nombreCompleto = List.of(
                        Objects.toString(cliente.getNombre(), ""),
                        Objects.toString(cliente.getSegundoNombre(), ""),
                        Objects.toString(cliente.getApellidoPaterno(), ""),
                        Objects.toString(cliente.getApellidoMaterno(), "")
                ).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" "));

        boolean bloqueado = usuario != null
                && usuario.getFechaBloqueo() != null
                && usuario.getFechaBloqueo().isAfter(ahora);

        List<CuentaResponse> cuentasResponse = cuentas.stream()
                .map(c -> new CuentaResponse(
                        c.getIdBancaria(),
                        cliente.getIdPersona(),
                        c.getNumeroCuenta(),
                        c.getClabe(),
                        c.getSaldo(),
                        c.isActiva(),
                        c.getCreadoEn()
                ))
                .toList();

        return new AdminUsuarioItemResponse(
                cliente.getIdPersona(),
                usuario != null ? usuario.getIdUsuario() : null,
                nombreCompleto,
                usuario != null ? usuario.getCorreo() : null,
                usuario != null ? usuario.getRol() : "ROLE_USER",
                cliente.isActivo() && (usuario == null || usuario.isActivo()),
                bloqueado,
                usuario != null ? usuario.getIntentosFallidos() : 0,
                usuario != null ? usuario.getFechaBloqueo() : null,
                usuario != null ? usuario.getPlataformaActiva() : null,
                cliente.getCurp(),
                cliente.getRfc(),
                contacto != null ? contacto.getTelefonoMovil() : null,
                cliente.getCreadoEn(),
                cuentasResponse
        );
    }
}
