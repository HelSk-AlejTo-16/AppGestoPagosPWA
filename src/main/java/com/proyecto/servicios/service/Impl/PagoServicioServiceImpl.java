package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.cliente.CuentaBancaria;
import com.proyecto.servicios.entity.cliente.Usuario;
import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import com.proyecto.servicios.entity.pagos.EstadoPagoServicio;
import com.proyecto.servicios.entity.pagos.PagoServicioEntity;
import com.proyecto.servicios.exception.AccesoNoAutorizadoException;
import com.proyecto.servicios.exception.BusinessConflictException;
import com.proyecto.servicios.exception.ErrorValidacionException;
import com.proyecto.servicios.exception.GestoPagoTimeoutException;
import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.model.cliente.PaginaResponse;
import com.proyecto.servicios.model.gestopago.tx.GestoPagoTxResponse;
import com.proyecto.servicios.model.pagos.PagoBusquedaRequest;
import com.proyecto.servicios.model.pagos.PagoConciliacionRequest;
import com.proyecto.servicios.model.pagos.PagoServicioRequest;
import com.proyecto.servicios.model.pagos.PagoServicioResponse;
import com.proyecto.servicios.model.pagos.RecargaTelefonicaRequest;
import com.proyecto.servicios.repositorys.cliente.CuentaBancariaRepository;
import com.proyecto.servicios.repositorys.cliente.UsuarioRepository;
import com.proyecto.servicios.repositorys.gestopago.ProductoPostgresRepository;
import com.proyecto.servicios.repositorys.pagos.PagoServicioRepository;
import com.proyecto.servicios.service.GestoPagoTxCommunicationService;
import com.proyecto.servicios.service.PagoServicioService;
import com.proyecto.servicios.util.MaskingUtils;
import com.proyecto.servicios.util.ReferenciaValidator;
import com.proyecto.servicios.util.UpcGenerator;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class PagoServicioServiceImpl implements PagoServicioService {

    private final PagoServicioRepository pagoRepository;
    private final CuentaBancariaRepository cuentaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProductoPostgresRepository productoRepository;
    private final GestoPagoTxCommunicationService txCommunicationService;
    private final PasswordEncoder passwordEncoder;

    public PagoServicioServiceImpl(PagoServicioRepository pagoRepository,
                                  CuentaBancariaRepository cuentaRepository,
                                  UsuarioRepository usuarioRepository,
                                  ProductoPostgresRepository productoRepository,
                                  GestoPagoTxCommunicationService txCommunicationService,
                                  PasswordEncoder passwordEncoder) {
        this.pagoRepository = pagoRepository;
        this.cuentaRepository = cuentaRepository;
        this.usuarioRepository = usuarioRepository;
        this.productoRepository = productoRepository;
        this.txCommunicationService = txCommunicationService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public PagoServicioResponse procesarPago(PagoServicioRequest request, String correoUsuarioAutenticado) {
        log.info("Iniciando procesamiento de pago de servicio: cuenta={}, idProducto={}, ref={}, monto={}",
                MaskingUtils.enmascararCuenta(request.numCuenta()),
                request.idProducto(),
                MaskingUtils.enmascararReferencia(request.referencia()),
                request.monto());

        // 1. Validar usuario autenticado y firma obligatoria (PIN/contraseña)
        validarFirmaYUsuario(correoUsuarioAutenticado, request.pinAutorizacion());

        // 2. Validar propiedad y estado operable de la cuenta bancaria
        CuentaBancaria cuenta = validarCuentaBancaria(request.numCuenta(), correoUsuarioAutenticado);

        // 3. Validar producto de servicios y referencia del recibo
        ProductoEntity producto = validarProductoServicio(request.idServicio(), request.idProducto(), request.referencia(), request.monto());

        // 4. Ejecutar flujo transaccional con débito y Two-Phase Rollback
        return ejecutarTransaccionInterna(
                cuenta,
                String.valueOf(producto.getIdServicio()),
                String.valueOf(producto.getIdProducto()),
                request.referencia(),
                request.monto(),
                producto,
                "Pago de servicio realizado con éxito."
        );
    }

    @Override
    public PagoServicioResponse procesarRecarga(RecargaTelefonicaRequest request, String correoUsuarioAutenticado) {
        log.info("Iniciando procesamiento de recarga telefónica: cuenta={}, idProducto={}, telefono={}, monto={}",
                MaskingUtils.enmascararCuenta(request.numCuenta()),
                request.idProducto(),
                MaskingUtils.enmascararReferencia(request.telefono()),
                request.monto());

        // 1. Validar usuario autenticado y firma obligatoria (PIN/contraseña)
        validarFirmaYUsuario(correoUsuarioAutenticado, request.pinAutorizacion());

        // 2. Validar propiedad y estado operable de la cuenta bancaria
        CuentaBancaria cuenta = validarCuentaBancaria(request.numCuenta(), correoUsuarioAutenticado);

        // 3. Validar producto de tiempo aire y número celular a 10 dígitos
        ProductoEntity producto = validarProductoRecarga(request.idServicio(), request.idProducto(), request.telefono(), request.monto());

        // 4. Ejecutar flujo transaccional con débito y Two-Phase Rollback
        return ejecutarTransaccionInterna(
                cuenta,
                String.valueOf(producto.getIdServicio()),
                String.valueOf(producto.getIdProducto()),
                request.telefono(),
                request.monto(),
                producto,
                "Recarga telefónica realizada con éxito."
        );
    }

    private PagoServicioResponse ejecutarTransaccionInterna(
            CuentaBancaria cuenta,
            String idServicio,
            String idProducto,
            String referenciaODestino,
            BigDecimal monto,
            ProductoEntity producto,
            String mensajeExito
    ) {
        String cuentaEnmascarada = MaskingUtils.enmascararCuenta(cuenta.getNumeroCuenta());
        String refEnmascarada = MaskingUtils.enmascararReferencia(referenciaODestino);

        // Prevención de Transacciones Duplicadas en ventana reciente (sendTx_duplicated)
        OffsetDateTime ventanaDuplicado = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5);
        boolean transaccionDuplicada = pagoRepository.existsByCuenta_IdBancariaAndIdProductoAndReferenciaAndMontoAndEstadoInAndCreadoEnAfter(
                cuenta.getIdBancaria(),
                idProducto,
                referenciaODestino,
                monto,
                List.of(EstadoPagoServicio.EXITOSO, EstadoPagoServicio.PENDIENTE, EstadoPagoServicio.PENDIENTE_CONCILIACION),
                ventanaDuplicado
        );
        if (transaccionDuplicada) {
            log.warn("Transacción duplicada detectada para cuenta {}, producto {}, ref {}, monto {}",
                    cuentaEnmascarada, idProducto, refEnmascarada, monto);
            throw new BusinessConflictException(
                    "sendTx_duplicated",
                    "Ya se procesó recientemente una transacción idéntica para este servicio y referencia. Para evitar cobros duplicados, por favor espera unos minutos."
            );
        }

        // Reserva Atómica de Saldo (Prevención de Doble Gasto)
        int filasDebito = cuentaRepository.debitarSaldo(cuenta.getIdBancaria(), monto);
        if (filasDebito == 0) {
            log.warn("Saldo insuficiente para cuenta {} al intentar debitar {}", cuentaEnmascarada, monto);
            throw new BusinessConflictException(
                    "sendTx_insufficientFunds",
                    "Tu cuenta no cuenta con saldo disponible suficiente para cubrir el monto de la operación."
            );
        }

        // Obtener nuevo saldo para el comprobante
        BigDecimal saldoRestante = cuentaRepository.findById(cuenta.getIdBancaria())
                .map(CuentaBancaria::getSaldo)
                .orElse(BigDecimal.ZERO);

        // Generar UPC único de idempotencia
        String upc = UpcGenerator.generarUpc();
        if (pagoRepository.existsByUpc(upc)) {
            upc = UpcGenerator.generarUpc();
        }

        // Registrar pago en estado PENDIENTE
        PagoServicioEntity pago = new PagoServicioEntity();
        pago.setCuenta(cuenta);
        pago.setIdServicio(idServicio);
        pago.setIdProducto(idProducto);
        pago.setNombreServicio(producto.getServicio());
        pago.setNombreProducto(producto.getProducto());
        pago.setReferencia(referenciaODestino);
        pago.setMonto(monto);
        pago.setUpc(upc);
        pago.setEstado(EstadoPagoServicio.PENDIENTE);
        pago = pagoRepository.saveAndFlush(pago);

        log.info("Reserva de saldo efectuada. Transacción {} registrada como PENDIENTE con UPC {}", pago.getIdPago(), upc);

        // Invocación síncrona a la pasarela GestoPago (sendTx.do)
        GestoPagoTxResponse gpResponse;
        try {
            gpResponse = txCommunicationService.ejecutarTransaccion(
                    idServicio,
                    idProducto,
                    referenciaODestino,
                    monto,
                    upc
            );
        } catch (GestoPagoTimeoutException timeoutEx) {
            // Protección de timeout: NO hacer rollback a ciegas; queda para conciliación y retorna HTTP 504
            log.error("Timeout de comunicación con GestoPago para UPC {}: {}", upc, timeoutEx.getMessage());
            pago.setEstado(EstadoPagoServicio.PENDIENTE_CONCILIACION);
            pago.setMensajeProveedor("Timeout de comunicación con proveedor: " + timeoutEx.getMessage());
            pagoRepository.save(pago);

            throw new GestoPagoTimeoutException(
                    "El proveedor del servicio no respondió a tiempo. Tu transacción (UPC: " + upc + ") quedó registrada en proceso de verificación para salvaguardar tus fondos.",
                    timeoutEx
            );
        } catch (Exception commEx) {
            // Falla de red previa a conexión: aplicar compensación / rollback inmediato
            log.error("Fallo general de conexión para UPC {} ({}). Aplicando rollback...", upc, commEx.getClass().getSimpleName());
            cuentaRepository.abonarSaldo(cuenta.getIdBancaria(), monto);
            pago.setEstado(EstadoPagoServicio.RECHAZADO);
            pago.setMensajeProveedor("Error de conexión: " + commEx.getMessage());
            pagoRepository.save(pago);

            throw new BusinessConflictException(
                    "ERROR_COMUNICACION",
                    "No fue posible establecer comunicación con el proveedor en este momento. Tu saldo ha sido reembolsado en su totalidad."
            );
        }

        // Evaluación de la respuesta del proveedor
        if (gpResponse != null && gpResponse.isAprobada()) {
            log.info("Transacción APROBADA por GestoPago para UPC {}. Folio: {}, Autorización: {}",
                    upc, gpResponse.getFolio(), gpResponse.getAutorizacion());
            pago.setEstado(EstadoPagoServicio.EXITOSO);
            pago.setCodigoRespuestaProveedor(gpResponse.getCodigo());
            pago.setFolioTransaccionGp(gpResponse.getFolio());
            pago.setFolioAutorizacion(gpResponse.getAutorizacion());
            pago.setMensajeProveedor(gpResponse.getTexto() != null ? gpResponse.getTexto() : "APROBADA");
            pago = pagoRepository.save(pago);

            return toResponse(pago, saldoRestante, mensajeExito);
        } else {
            // COMPENSACIÓN INMEDIATA (ROLLBACK) POR RECHAZO DEL PROVEEDOR
            String motivo = gpResponse != null && gpResponse.getTexto() != null ? gpResponse.getTexto() : "Transacción declinada por carrier";
            String codigo = gpResponse != null && gpResponse.getCodigo() != null ? gpResponse.getCodigo() : "99";

            log.warn("GestoPago rechazó la transacción para UPC {}: Código {}, Motivo: {}. Aplicando reversa...",
                    upc, codigo, motivo);

            cuentaRepository.abonarSaldo(cuenta.getIdBancaria(), monto);
            pago.setEstado(EstadoPagoServicio.RECHAZADO);
            pago.setCodigoRespuestaProveedor(codigo);
            pago.setMensajeProveedor(motivo);
            pagoRepository.save(pago);

            boolean esDuplicado = "03".equals(codigo)
                    || motivo.toLowerCase(Locale.ROOT).contains("duplicad")
                    || motivo.contains("sendTx_duplicated");

            String codigoExcepcion = esDuplicado ? "sendTx_duplicated" : "GESTOPAGO_RECHAZADO";
            String mensajeExcepcion = esDuplicado
                    ? "La compañía proveedora detectó una transacción duplicada para esta referencia. Tu saldo ha sido reembolsado en su totalidad."
                    : "La transacción no pudo ser completada por la compañía: " + motivo + ". El monto ha sido reembolsado automáticamente a su cuenta.";

            throw new BusinessConflictException(codigoExcepcion, mensajeExcepcion);
        }
    }

    @Override
    @Transactional
    public PagoServicioResponse conciliarPago(
            PagoConciliacionRequest request,
            String correoUsuarioAutenticado,
            boolean esAdmin
    ) {
        String upc = request.upc().trim();
        PagoServicioEntity pago = pagoRepository.findByUpc(upc)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "PAGO_NO_ENCONTRADO",
                        "No se encontró ninguna transacción asociada al código UPC proporcionado."
                ));

        // Control de acceso: solo el titular de la cuenta o un administrador pueden conciliar la operación
        if (!esAdmin) {
            if (pago.getCuenta() == null
                    || pago.getCuenta().getCliente() == null
                    || pago.getCuenta().getCliente().getUsuario() == null
                    || !pago.getCuenta().getCliente().getUsuario().getCorreo().equalsIgnoreCase(correoUsuarioAutenticado)) {
                throw new AccessDeniedException("No tienes autorización para conciliar esta transacción.");
            }
        }

        if (pago.getEstado() != EstadoPagoServicio.PENDIENTE_CONCILIACION) {
            throw new BusinessConflictException(
                    "CONCILIACION_NO_PROCEDENTE",
                    "La transacción con UPC " + upc + " no se encuentra pendiente de conciliación (estado actual: " + pago.getEstado() + ")."
            );
        }

        String cuentaEnmascarada = MaskingUtils.enmascararCuenta(pago.getCuenta().getNumeroCuenta());
        log.info("Iniciando conciliación de transacción UPC {} para cuenta {}", upc, cuentaEnmascarada);

        // Escenario 1: Resolución explícita tras validación con el portal del proveedor
        if (request.procesadaPorProveedor() != null) {
            if (Boolean.TRUE.equals(request.procesadaPorProveedor())) {
                pago.setEstado(EstadoPagoServicio.EXITOSO);
                pago.setCodigoRespuestaProveedor("01");
                pago.setFolioTransaccionGp(request.folioTransaccionGp() != null && !request.folioTransaccionGp().isBlank()
                        ? request.folioTransaccionGp().trim()
                        : "GP-CONC-" + upc);
                pago.setFolioAutorizacion(request.folioAutorizacion() != null && !request.folioAutorizacion().isBlank()
                        ? request.folioAutorizacion().trim()
                        : "AUTH-CONC-" + upc);
                pago.setMensajeProveedor(request.observacion() != null && !request.observacion().isBlank()
                        ? request.observacion().trim()
                        : "Transacción confirmada mediante conciliación con proveedor.");
                pago = pagoRepository.save(pago);

                BigDecimal saldoActual = cuentaRepository.findById(pago.getCuenta().getIdBancaria())
                        .map(CuentaBancaria::getSaldo)
                        .orElse(pago.getCuenta().getSaldo());
                log.info("Conciliación para UPC {} resuelta como EXITOSO.", upc);
                return toResponse(pago, saldoActual, "Conciliación completada: la operación fue confirmada como exitosa.");
            } else {
                cuentaRepository.abonarSaldo(pago.getCuenta().getIdBancaria(), pago.getMonto());
                pago.setEstado(EstadoPagoServicio.REVERTIDO);
                pago.setCodigoRespuestaProveedor("98");
                pago.setMensajeProveedor(request.observacion() != null && !request.observacion().isBlank()
                        ? request.observacion().trim()
                        : "Operación no procesada por el proveedor tras timeout. Saldo reintegrado por conciliación.");
                pago = pagoRepository.save(pago);

                BigDecimal saldoRestaurado = cuentaRepository.findById(pago.getCuenta().getIdBancaria())
                        .map(CuentaBancaria::getSaldo)
                        .orElse(pago.getCuenta().getSaldo());
                log.info("Conciliación para UPC {} resuelta como REVERTIDO. Saldo reintegrado a cuenta {}.", upc, cuentaEnmascarada);
                return toResponse(pago, saldoRestaurado, "Conciliación completada: se confirmó que el cargo no procedió en el proveedor y tu saldo ha sido reintegrado.");
            }
        }

        // Escenario 2: Verificación automática por idempotencia reutilizando el mismo UPC original
        GestoPagoTxResponse gpResponse = txCommunicationService.ejecutarTransaccion(
                pago.getIdServicio(),
                pago.getIdProducto(),
                pago.getReferencia(),
                pago.getMonto(),
                pago.getUpc()
        );

        String codigo = gpResponse != null && gpResponse.getCodigo() != null ? gpResponse.getCodigo() : "99";
        String motivo = gpResponse != null && gpResponse.getTexto() != null ? gpResponse.getTexto() : "";
        boolean yaProcesadaPorIdempotencia = "03".equals(codigo)
                || motivo.toLowerCase(Locale.ROOT).contains("duplicad")
                || motivo.contains("sendTx_duplicated");

        if ((gpResponse != null && gpResponse.isAprobada()) || yaProcesadaPorIdempotencia) {
            pago.setEstado(EstadoPagoServicio.EXITOSO);
            pago.setCodigoRespuestaProveedor("01");
            pago.setFolioTransaccionGp(gpResponse != null && gpResponse.getFolio() != null
                    ? gpResponse.getFolio()
                    : "GP-IDEMP-" + upc);
            pago.setFolioAutorizacion(gpResponse != null && gpResponse.getAutorizacion() != null
                    ? gpResponse.getAutorizacion()
                    : "AUTH-IDEMP-" + upc);
            pago.setMensajeProveedor(yaProcesadaPorIdempotencia
                    ? "Confirmada por idempotencia del proveedor (UPC previamente procesado)."
                    : (motivo.isBlank() ? "APROBADA EN CONCILIACION" : motivo));
            pago = pagoRepository.save(pago);

            BigDecimal saldoActual = cuentaRepository.findById(pago.getCuenta().getIdBancaria())
                    .map(CuentaBancaria::getSaldo)
                    .orElse(pago.getCuenta().getSaldo());
            log.info("Conciliación idempotente para UPC {} confirmada como EXITOSO.", upc);
            return toResponse(pago, saldoActual, "Conciliación completada: la operación fue confirmada como exitosa con el proveedor.");
        } else {
            cuentaRepository.abonarSaldo(pago.getCuenta().getIdBancaria(), pago.getMonto());
            pago.setEstado(EstadoPagoServicio.REVERTIDO);
            pago.setCodigoRespuestaProveedor(codigo);
            pago.setMensajeProveedor("Rechazada en verificación de conciliación (" + motivo + "). Saldo reintegrado.");
            pago = pagoRepository.save(pago);

            BigDecimal saldoRestaurado = cuentaRepository.findById(pago.getCuenta().getIdBancaria())
                    .map(CuentaBancaria::getSaldo)
                    .orElse(pago.getCuenta().getSaldo());
            log.info("Conciliación idempotente para UPC {} resultó no procesada. Saldo reintegrado a cuenta {}.", upc, cuentaEnmascarada);
            return toResponse(pago, saldoRestaurado, "Conciliación completada: el proveedor confirmó que la operación no procedió y tu saldo ha sido reintegrado.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaResponse<PagoServicioResponse> consultarHistorial(
            PagoBusquedaRequest request,
            String correoUsuarioAutenticado,
            boolean esAdmin
    ) {
        Pageable pageable = PageRequest.of(request.pagina(), request.tamano(), Sort.by(Sort.Direction.DESC, "creadoEn"));

        Specification<PagoServicioEntity> spec = (root, query, cb) -> {
            List<Predicate> filtros = new ArrayList<>();

            // Control de acceso RBAC: Un usuario común solo ve pagos de sus cuentas
            if (!esAdmin) {
                filtros.add(cb.equal(
                        cb.lower(root.join("cuenta").join("cliente").join("usuario").get("correo")),
                        correoUsuarioAutenticado == null ? "" : correoUsuarioAutenticado.trim().toLowerCase(Locale.ROOT)
                ));
            }

            if (request.numeroCuenta() != null && !request.numeroCuenta().isBlank()) {
                filtros.add(cb.equal(root.join("cuenta").get("numeroCuenta"), request.numeroCuenta().trim()));
            }

            if (request.estado() != null) {
                filtros.add(cb.equal(root.get("estado"), request.estado()));
            }

            if (request.upc() != null && !request.upc().isBlank()) {
                filtros.add(cb.equal(root.get("upc"), request.upc().trim()));
            }

            if (request.referencia() != null && !request.referencia().isBlank()) {
                filtros.add(cb.like(cb.lower(root.get("referencia")), "%" + request.referencia().trim().toLowerCase(Locale.ROOT) + "%"));
            }

            if (request.idServicio() != null && !request.idServicio().isBlank()) {
                filtros.add(cb.equal(root.get("idServicio"), request.idServicio().trim()));
            }

            if (request.idProducto() != null && !request.idProducto().isBlank()) {
                filtros.add(cb.equal(root.get("idProducto"), request.idProducto().trim()));
            }

            return cb.and(filtros.toArray(new Predicate[0]));
        };

        Page<PagoServicioResponse> pagina = pagoRepository.findAll(spec, pageable)
                .map(p -> toResponse(p, p.getCuenta().getSaldo(), p.getMensajeProveedor()));

        return PaginaResponse.desde(pagina);
    }

    // ==========================================
    // Métodos Auxiliares de Validación
    // ==========================================

    private ProductoEntity validarProductoServicio(String idServicioStr, String idProductoStr, String referencia, BigDecimal monto) {
        ProductoEntity producto = obtenerYValidarProductoBase(idServicioStr, idProductoStr);

        if (producto.getTipoFront() != null && producto.getTipoFront() == 1) {
            throw new BusinessConflictException(
                    "OPERACION_NO_PERMITIDA",
                    "El producto seleccionado corresponde a una recarga de tiempo aire. Por favor, realiza la operación desde el módulo de recargas."
            );
        }

        validarPrecioFijoSiAplica(producto, monto);
        ReferenciaValidator.validarReferencia(referencia, producto.getTipoFront(), producto.getHasDigitoVerificador());

        return producto;
    }

    private ProductoEntity validarProductoRecarga(String idServicioStr, String idProductoStr, String telefono, BigDecimal monto) {
        ProductoEntity producto = obtenerYValidarProductoBase(idServicioStr, idProductoStr);

        if (producto.getTipoFront() != null && producto.getTipoFront() != 1) {
            throw new BusinessConflictException(
                    "OPERACION_NO_PERMITIDA",
                    "El producto seleccionado corresponde a un pago de servicio. Por favor, realiza la operación desde el módulo de pago de servicios."
            );
        }

        validarPrecioFijoSiAplica(producto, monto);
        ReferenciaValidator.validarReferencia(telefono, 1, false);

        return producto;
    }

    private ProductoEntity obtenerYValidarProductoBase(String idServicioStr, String idProductoStr) {
        Integer idServicio;
        Integer idProducto;
        try {
            idServicio = Integer.parseInt(idServicioStr.trim());
            idProducto = Integer.parseInt(idProductoStr.trim());
        } catch (NumberFormatException e) {
            throw new ErrorValidacionException("El identificador del servicio o producto seleccionado no es válido.");
        }

        ProductoEntity producto = productoRepository.findByIdProducto(idProducto)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "PRODUCTO_NO_ENCONTRADO", "El producto o servicio seleccionado no se encuentra en el catálogo disponible."
                ));

        if (!Boolean.TRUE.equals(producto.getActivo())) {
            throw new BusinessConflictException(
                    "PRODUCTO_INACTIVO", "El producto o servicio seleccionado no está disponible en este momento."
            );
        }

        if (producto.getIdServicio() != null && !producto.getIdServicio().equals(idServicio)) {
            throw new BusinessConflictException(
                    "SERVICIO_NO_COINCIDE",
                    "El producto seleccionado no pertenece a la compañía o servicio indicado. Por favor, verifica tu selección."
            );
        }

        return producto;
    }

    private void validarPrecioFijoSiAplica(ProductoEntity producto, BigDecimal monto) {
        if (producto.getPrecio() != null && producto.getPrecio() > 0) {
            BigDecimal precioCatalogo = BigDecimal.valueOf(producto.getPrecio()).setScale(2, RoundingMode.HALF_UP);
            if (monto.setScale(2, RoundingMode.HALF_UP).compareTo(precioCatalogo) != 0) {
                throw new BusinessConflictException(
                        "MONTO_INVALIDO",
                        "El paquete o servicio seleccionado tiene un precio fijo de $" + precioCatalogo + ". El monto ingresado ($" + monto + ") debe coincidir exactamente."
                );
            }
        }
    }

    private Usuario validarFirmaYUsuario(String correo, String pinAutorizacion) {
        Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(correo)
                .orElseThrow(() -> new AccessDeniedException("No fue posible identificar tu sesión de usuario."));

        if (!usuario.isActivo()) {
            throw new BusinessConflictException("USUARIO_INACTIVO", "Tu usuario se encuentra inactivo. Por favor, contacta a soporte.");
        }

        if (usuario.getFechaBloqueo() != null && usuario.getFechaBloqueo().isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new BusinessConflictException(
                    "USUARIO_BLOQUEADO",
                    "Por motivos de seguridad, tu cuenta ha sido bloqueada temporalmente por 15 minutos debido a múltiples intentos incorrectos."
            );
        }

        if (!passwordEncoder.matches(pinAutorizacion, usuario.getPassword())) {
            int intentos = usuario.getIntentosFallidos() + 1;
            usuario.setIntentosFallidos(intentos);

            if (intentos >= 3) {
                usuario.setFechaBloqueo(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15));
                usuarioRepository.save(usuario);
                throw new BusinessConflictException(
                        "USUARIO_BLOQUEADO",
                        "Has superado los 3 intentos fallidos de autorización. Por seguridad, tu cuenta ha sido bloqueada durante 15 minutos."
                );
            } else {
                usuarioRepository.save(usuario);
                throw new AccesoNoAutorizadoException(
                        "PIN_INVALIDO",
                        "La contraseña de autorización es incorrecta. Intentos restantes: " + (3 - intentos),
                        401
                );
            }
        }

        // Restablecer contador de intentos en caso de éxito
        if (usuario.getIntentosFallidos() > 0) {
            usuario.setIntentosFallidos(0);
            usuario.setFechaBloqueo(null);
            usuarioRepository.save(usuario);
        }

        return usuario;
    }

    private CuentaBancaria validarCuentaBancaria(String numeroCuenta, String correoAutenticado) {
        CuentaBancaria cuenta = cuentaRepository.findByNumeroCuenta(numeroCuenta)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "CUENTA_NO_ENCONTRADA", "No se encontró la cuenta bancaria especificada."
                ));

        if (cuenta.getCliente() == null || cuenta.getCliente().getUsuario() == null
                || !cuenta.getCliente().getUsuario().getCorreo().equalsIgnoreCase(correoAutenticado)) {
            throw new AccessDeniedException("No tienes autorización para realizar operaciones con esta cuenta bancaria.");
        }

        if (!cuenta.isActiva() || !cuenta.getCliente().isActivo()) {
            throw new BusinessConflictException(
                    "CUENTA_INACTIVA", "La cuenta bancaria o el cliente se encuentran inactivos."
            );
        }

        return cuenta;
    }

    private PagoServicioResponse toResponse(PagoServicioEntity p, BigDecimal saldo, String mensaje) {
        return new PagoServicioResponse(
                p.getIdPago(),
                p.getCuenta().getNumeroCuenta(),
                p.getIdServicio(),
                p.getIdProducto(),
                p.getNombreServicio(),
                p.getNombreProducto(),
                p.getReferencia(),
                p.getMonto(),
                p.getUpc(),
                p.getEstado(),
                p.getFolioAutorizacion(),
                p.getFolioTransaccionGp(),
                saldo,
                mensaje,
                p.getCreadoEn()
        );
    }
}
