package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.GestoPagoProductClient;
import com.proyecto.servicios.entity.gestopago.ProductoDocument;
import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import com.proyecto.servicios.exception.CatalogUnavailableException;
import com.proyecto.servicios.exception.GestoPagoAuthException;
import com.proyecto.servicios.exception.GestoPagoCommunicationException;
import com.proyecto.servicios.exception.GestoPagoIntegrationException;
import com.proyecto.servicios.exception.GestoPagoResponseException;
import com.proyecto.servicios.exception.GestoPagoTimeoutException;
import com.proyecto.servicios.model.cliente.PaginaResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoMensaje;
import com.proyecto.servicios.model.gestopago.GestoPagoProductResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoProducto;
import com.proyecto.servicios.model.gestopago.ProductoBusquedaRequest;
import com.proyecto.servicios.model.gestopago.ServicioResumenDto;
import com.proyecto.servicios.repositorys.gestopago.ProductoMongoRepository;
import com.proyecto.servicios.repositorys.gestopago.ProductoPostgresRepository;
import com.proyecto.servicios.service.GestoPagoProductService;
import com.proyecto.servicios.service.GestoPagoTokenService;
import com.proyecto.servicios.service.NotificationService;
import feign.FeignException;
import feign.RetryableException;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.net.SocketTimeoutException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class GestoPagoProductServiceImpl implements GestoPagoProductService {

    private static final String CODIGO_EXITO = "01";

    private final GestoPagoProductClient productClient;
    private final GestoPagoTokenService tokenService;
    private final ProductoMongoRepository mongoRepository;
    private final ProductoPostgresRepository postgresRepository;
    private final NotificationService notificationService;

    @Value("${gestopago.auth.id-distribuidor}")
    private Integer idDistribuidor;

    @Value("${gestopago.auth.codigo-dispositivo}")
    private String codigoDispositivo;

    public GestoPagoProductServiceImpl(GestoPagoProductClient productClient,
                                       GestoPagoTokenService tokenService,
                                       ProductoMongoRepository mongoRepository,
                                       ProductoPostgresRepository postgresRepository,
                                       NotificationService notificationService) {
        this.productClient = productClient;
        this.tokenService = tokenService;
        this.mongoRepository = mongoRepository;
        this.postgresRepository = postgresRepository;
        this.notificationService = notificationService;
    }

    // ============================
    // Punto de entrada (con logs de inicio y fin)
    // ============================

    @Override
    public GestoPagoProductResponse obtenerProductos() {
        long inicio = System.currentTimeMillis();
        log.info("Inicio de consulta del catálogo de productos");
        try {
            return consultarCatalogo();
        } finally {
            log.info("Fin de consulta del catálogo de productos ({} ms)", System.currentTimeMillis() - inicio);
        }
    }

    @Override
    public PaginaResponse<GestoPagoProducto> buscarProductosPaginados(ProductoBusquedaRequest request) {
        ProductoBusquedaRequest req = request != null
                ? request
                : new ProductoBusquedaRequest(0, 10, null, null, null);
        List<GestoPagoProducto> filtrados = filtrarCatalogo(obtenerProductos().getProductos(), req).stream()
                .sorted(Comparator
                        .comparing((GestoPagoProducto p) -> textoSeguro(p.getServicio()), String.CASE_INSENSITIVE_ORDER)
                        .thenComparingDouble(p -> p.getPrecio() != null ? p.getPrecio() : 0.0)
                        .thenComparing(p -> textoSeguro(p.getProducto()), String.CASE_INSENSITIVE_ORDER))
                .toList();
        return paginarLista(filtrados, req.paginaSegura(), req.tamanoSeguro());
    }

    @Override
    public PaginaResponse<ServicioResumenDto> buscarServiciosPaginados(ProductoBusquedaRequest request) {
        ProductoBusquedaRequest req = request != null
                ? request
                : new ProductoBusquedaRequest(0, 8, null, null, null);
        List<GestoPagoProducto> filtrados = filtrarCatalogo(obtenerProductos().getProductos(), req);

        Map<String, List<GestoPagoProducto>> agrupados = new LinkedHashMap<>();
        for (GestoPagoProducto p : filtrados) {
            String clave = textoSeguro(p.getServicio()).toLowerCase(Locale.ROOT);
            agrupados.computeIfAbsent(clave, k -> new java.util.ArrayList<>()).add(p);
        }

        List<ServicioResumenDto> servicios = agrupados.values().stream()
                .map(lista -> {
                    GestoPagoProducto base = lista.get(0);
                    double min = lista.stream()
                            .mapToDouble(i -> i.getPrecio() != null ? i.getPrecio() : 0.0)
                            .min()
                            .orElse(0.0);
                    double max = lista.stream()
                            .mapToDouble(i -> i.getPrecio() != null ? i.getPrecio() : 0.0)
                            .max()
                            .orElse(0.0);
                    return new ServicioResumenDto(
                            base.getIdServicio(),
                            textoSeguro(base.getServicio()),
                            base.getTipoFront(),
                            clasificarCategoria(base),
                            lista.size(),
                            min,
                            max
                    );
                })
                .sorted(Comparator.comparing(ServicioResumenDto::servicio, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return paginarLista(servicios, req.paginaSegura(), req.tamanoSeguro());
    }

    // ============================
    // Cadena de respaldo: PostgreSQL, MongoDB y API directa
    // ============================

    private GestoPagoProductResponse consultarCatalogo() {
        try {
            return desdePostgres();
        } catch (Exception ePg) {
            notificationService.alertFallbackTriggered("MongoDB",
                    "Fallo al obtener datos de PostgreSQL: " + ePg.getMessage());
        }

        try {
            return desdeMongo();
        } catch (Exception eMongo) {
            notificationService.alertFallbackTriggered("API Directa GestoPago",
                    "Fallo de base de datos MongoDB: " + eMongo.getMessage());
        }

        return desdeApiExterna();
    }

    private GestoPagoProductResponse desdeMongo() {
        List<ProductoDocument> docs = mongoRepository.findAll();
        if (docs.isEmpty()) {
            throw new IllegalStateException("MongoDB vacío");
        }
        log.info("Catálogo obtenido exitosamente desde MongoDB (Respaldo)");
        List<GestoPagoProducto> productos = docs.stream()
                .filter(d -> Boolean.TRUE.equals(d.getActivo()))
                .map(this::mapMongoToDto)
                .toList();
        return armarRespuesta("Catálogo obtenido de MongoDB (Respaldo)", productos);
    }

    private GestoPagoProductResponse desdePostgres() {
        List<ProductoEntity> entidades = postgresRepository.findAll();
        if (entidades.isEmpty()) {
            throw new IllegalStateException("PostgreSQL vacío");
        }
        log.info("Catálogo obtenido exitosamente desde PostgreSQL");
        List<GestoPagoProducto> productos = entidades.stream()
                .filter(e -> Boolean.TRUE.equals(e.getActivo()))
                .map(this::mapPostgresToDto)
                .toList();
        return armarRespuesta("Catálogo obtenido de PostgreSQL local", productos);
    }

    private GestoPagoProductResponse desdeApiExterna() {
        try {
            GestoPagoProductResponse response = consultarApiExterna();
            log.info("Catálogo obtenido exitosamente desde la API Externa de GestoPago");
            response.getMensaje().setTexto(response.getMensaje().getTexto() + " (Fuente: API Externa Directa)");
            return response;
        } catch (GestoPagoIntegrationException e) {
            // Error de integración ya clasificado: lo resuelve el GlobalExceptionHandler.
            log.error("Fallo crítico: no se pudo obtener el catálogo de ninguna fuente ({})",
                    e.getClass().getSimpleName());
            throw e;
        } catch (Exception e) {
            log.error("Fallo crítico inesperado al consultar GestoPago ({})",
                    e.getClass().getSimpleName());
            throw new CatalogUnavailableException(
                    "El servicio de catálogo de productos no está disponible temporalmente. Intente más tarde.");
        }
    }

    // ============================
    // Integración con GestoPago (clasificación de errores)
    // ============================

    private GestoPagoProductResponse consultarApiExterna() {
        String bearerToken = obtenerBearerToken();

        log.info("Invocando GestoPago getProductList");
        try {
            String xml = productClient.getProductList(bearerToken);
            GestoPagoProductResponse response = parsearXml(xml);
            log.info("Respuesta de GestoPago recibida correctamente");
            return response;
        } catch (FeignException.Unauthorized | FeignException.Forbidden e) {
            throw new GestoPagoAuthException("GestoPago rechazó la autenticación (HTTP " + e.status() + ")", e);
        } catch (RetryableException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                throw new GestoPagoTimeoutException("Timeout al invocar GestoPago", e);
            }
            throw new GestoPagoCommunicationException("Error de comunicación con GestoPago", e);
        } catch (FeignException e) {
            throw new GestoPagoResponseException("GestoPago respondió con HTTP " + e.status(), e);
        }
    }

    private String obtenerBearerToken() {
        try {
            return tokenService.obtenerBearerToken(idDistribuidor, codigoDispositivo);
        } catch (IllegalStateException e) {
            throw new GestoPagoAuthException("No hay token disponible para consultar GestoPago", e);
        }
    }

    private GestoPagoProductResponse parsearXml(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new GestoPagoResponseException("GestoPago devolvió una respuesta vacía", null);
        }
        try {
            Unmarshaller unmarshaller = JAXBContext.newInstance(GestoPagoProductResponse.class).createUnmarshaller();
            GestoPagoProductResponse response =
                    (GestoPagoProductResponse) unmarshaller.unmarshal(new StringReader(xml));
            validarRespuesta(response);
            return response;
        } catch (JAXBException e) {
            throw new GestoPagoResponseException("El XML devuelto por GestoPago no es válido", e);
        }
    }

    private void validarRespuesta(GestoPagoProductResponse response) {
        if (response == null || response.getMensaje() == null) {
            throw new GestoPagoResponseException("La respuesta de GestoPago no contiene el mensaje esperado", null);
        }
        if (!CODIGO_EXITO.equals(response.getMensaje().getCodigo())) {
            throw new GestoPagoResponseException(
                    "GestoPago devolvió un código no exitoso: " + response.getMensaje().getCodigo(), null);
        }
    }

    // ============================
    // Utilidades
    // ============================

    private GestoPagoProductResponse armarRespuesta(String texto, List<GestoPagoProducto> productos) {
        GestoPagoMensaje mensaje = new GestoPagoMensaje();
        mensaje.setCodigo("00");
        mensaje.setTexto(texto);
        GestoPagoProductResponse response = new GestoPagoProductResponse();
        response.setMensaje(mensaje);
        response.setProductos(productos);
        return response;
    }

    private GestoPagoProducto mapMongoToDto(ProductoDocument doc) {
        GestoPagoProducto p = new GestoPagoProducto();
        p.setIdProducto(doc.getIdProducto());
        p.setIdServicio(doc.getIdServicio());
        p.setServicio(doc.getServicio());
        p.setProducto(doc.getProducto());
        p.setPrecio(doc.getPrecio());
        p.setTipoFront(doc.getTipoFront());
        p.setTipoReferencia(doc.getTipoReferencia());
        p.setHasDigitoVerificador(doc.getHasDigitoVerificador());
        p.setLegend(doc.getLegend());
        return p;
    }

    private GestoPagoProducto mapPostgresToDto(ProductoEntity entity) {
        GestoPagoProducto p = new GestoPagoProducto();
        p.setIdProducto(entity.getIdProducto());
        p.setIdServicio(entity.getIdServicio());
        p.setServicio(entity.getServicio());
        p.setProducto(entity.getProducto());
        p.setPrecio(entity.getPrecio());
        p.setTipoFront(entity.getTipoFront());
        p.setTipoReferencia(entity.getTipoReferencia());
        p.setHasDigitoVerificador(entity.getHasDigitoVerificador());
        p.setLegend(entity.getLegend());
        return p;
    }

    private List<GestoPagoProducto> filtrarCatalogo(List<GestoPagoProducto> base, ProductoBusquedaRequest req) {
        if (base == null || base.isEmpty()) {
            return List.of();
        }
        String categoria = req.categoria() != null ? req.categoria().trim() : "";
        String servicio = req.servicio() != null ? req.servicio().trim().toLowerCase(Locale.ROOT) : "";
        String query = req.busqueda() != null ? req.busqueda().trim().toLowerCase(Locale.ROOT) : "";

        return base.stream()
                .filter(p -> {
                    if (!categoria.isEmpty() && !"todos".equalsIgnoreCase(categoria)) {
                        if (!clasificarCategoria(p).equalsIgnoreCase(categoria)) {
                            return false;
                        }
                    }
                    if (!servicio.isEmpty()) {
                        if (!textoSeguro(p.getServicio()).toLowerCase(Locale.ROOT).equals(servicio)) {
                            return false;
                        }
                    }
                    if (!query.isEmpty()) {
                        String nomServ = textoSeguro(p.getServicio()).toLowerCase(Locale.ROOT);
                        String nomProd = textoSeguro(p.getProducto()).toLowerCase(Locale.ROOT);
                        return nomServ.contains(query) || nomProd.contains(query);
                    }
                    return true;
                })
                .toList();
    }

    private static <T> PaginaResponse<T> paginarLista(List<T> items, int pagina, int tamano) {
        int totalElementos = items.size();
        int totalPaginas = totalElementos == 0 ? 1 : (int) Math.ceil((double) totalElementos / tamano);
        int paginaEfectiva = Math.max(0, Math.min(pagina, totalPaginas - 1));
        int desde = paginaEfectiva * tamano;
        int hasta = Math.min(desde + tamano, totalElementos);
        List<T> sublista = (desde >= totalElementos) ? List.of() : items.subList(desde, hasta);

        return new PaginaResponse<>(
                sublista,
                paginaEfectiva,
                tamano,
                totalElementos,
                totalPaginas,
                paginaEfectiva == 0,
                paginaEfectiva >= totalPaginas - 1
        );
    }

    private static String clasificarCategoria(GestoPagoProducto p) {
        if (p.getTipoFront() != null && p.getTipoFront() == 1) {
            return "Telefonía";
        }
        String nombre = textoSeguro(p.getServicio()).toUpperCase(Locale.ROOT);
        if (nombre.contains("CFE") || nombre.contains("LUZ") || nombre.contains("ELECTR")) {
            return "Electricidad";
        }
        if (nombre.contains("AGUA") || nombre.contains("SIAPA") || nombre.contains("SACMEX") || nombre.contains("DAPA")) {
            return "Agua";
        }
        if (nombre.contains("TELMEX") || nombre.contains("TOTALPLAY") || nombre.contains("IZZI") || nombre.contains("MEGACABLE")) {
            return "Internet y TV";
        }
        if (nombre.contains("GAS") || nombre.contains("NATURGY")) {
            return "Gas";
        }
        if (nombre.contains("NETFLIX") || nombre.contains("SPOTIFY") || nombre.contains("AMAZON") || nombre.contains("UBER")) {
            return "Entretenimiento";
        }
        return "Servicios Públicos";
    }

    private static String textoSeguro(String valor) {
        return valor == null ? "" : valor.trim();
    }
}
