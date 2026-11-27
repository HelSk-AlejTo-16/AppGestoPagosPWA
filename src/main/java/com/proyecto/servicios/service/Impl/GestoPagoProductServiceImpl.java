package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.GestoPagoProductClient;
import com.proyecto.servicios.entity.gestopago.ProductoDocument;
import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import com.proyecto.servicios.entity.gestopago.ServicioLogoEntity;
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
import com.proyecto.servicios.repositorys.gestopago.ServicioLogoRepository;
import com.proyecto.servicios.service.GestoPagoProductService;
import com.proyecto.servicios.service.GestoPagoTokenService;
import com.proyecto.servicios.service.NotificationService;
import feign.FeignException;
import feign.RetryableException;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

@Slf4j
@Service
public class GestoPagoProductServiceImpl implements GestoPagoProductService {

    private static final String CODIGO_EXITO = "01";
    private final Map<String, byte[]> logoCache = new ConcurrentHashMap<>();

    private final GestoPagoProductClient productClient;
    private final GestoPagoTokenService tokenService;
    private final ProductoMongoRepository mongoRepository;
    private final ProductoPostgresRepository postgresRepository;
    private final NotificationService notificationService;
    private final ServicioLogoRepository servicioLogoRepository;
    private final HttpClient logoHttpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Value("${gestopago.auth.id-distribuidor}")
    private Integer idDistribuidor;

    @Value("${gestopago.auth.codigo-dispositivo}")
    private String codigoDispositivo;

    public GestoPagoProductServiceImpl(GestoPagoProductClient productClient,
                                       GestoPagoTokenService tokenService,
                                       ProductoMongoRepository mongoRepository,
                                       ProductoPostgresRepository postgresRepository,
                                       NotificationService notificationService) {
        this(productClient, tokenService, mongoRepository, postgresRepository, notificationService, null);
    }

    @Autowired
    public GestoPagoProductServiceImpl(GestoPagoProductClient productClient,
                                       GestoPagoTokenService tokenService,
                                       ProductoMongoRepository mongoRepository,
                                       ProductoPostgresRepository postgresRepository,
                                       NotificationService notificationService,
                                       ServicioLogoRepository servicioLogoRepository) {
        this.productClient = productClient;
        this.tokenService = tokenService;
        this.mongoRepository = mongoRepository;
        this.postgresRepository = postgresRepository;
        this.notificationService = notificationService;
        this.servicioLogoRepository = servicioLogoRepository;
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
                            max,
                            construirLogoUrl(base.getIdServicio(), base.getServicio())
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
        p.setLogoUrl(construirLogoUrl(doc.getIdServicio(), doc.getServicio()));
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
        p.setLogoUrl(construirLogoUrl(entity.getIdServicio(), entity.getServicio()));
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

    @Override
    public byte[] getServiceLogo(Integer idServicio, String nombreServicio) {
        String nombre = textoSeguro(nombreServicio);
        if (nombre.isEmpty() && idServicio != null) {
            nombre = postgresRepository.findAll().stream()
                    .filter(p -> idServicio.equals(p.getIdServicio()) && p.getServicio() != null)
                    .map(ProductoEntity::getServicio)
                    .findFirst()
                    .orElse("Servicio");
        }
        if (nombre.isEmpty()) {
            nombre = "Servicio";
        }

        String cacheKey = (idServicio != null ? idServicio : 0) + ":" + nombre.toUpperCase(Locale.ROOT);
        byte[] cached = logoCache.get(cacheKey);
        if (cached != null && cached.length > 0) {
            return cached;
        }

        // 1. Consultar en PostgreSQL para evitar peticiones repetidas a GestoPago / externos
        if (idServicio != null && idServicio > 0 && servicioLogoRepository != null) {
            try {
                Optional<ServicioLogoEntity> enBd = servicioLogoRepository.findById(idServicio);
                if (enBd.isPresent() && enBd.get().getLogoData() != null && enBd.get().getLogoData().length > 0) {
                    byte[] datos = enBd.get().getLogoData();
                    logoCache.put(cacheKey, datos);
                    return datos;
                }
            } catch (Exception e) {
                log.debug("No se pudo leer logo desde PostgreSQL para idServicio={}: {}", idServicio, e.getMessage());
            }
        }

        byte[] logoObtenido = null;
        String fuente = "LOCAL_PNG";

        // 2. Intentar obtener directamente de GestoPago
        if (idServicio != null && idServicio > 0) {
            try {
                String bearer = obtenerBearerToken();
                try (feign.Response gpResp = productClient.getServiceLogo(bearer, idServicio)) {
                    if (gpResp != null && gpResp.status() == 200 && gpResp.body() != null) {
                        var contentTypes = gpResp.headers().getOrDefault("content-type", List.of());
                        boolean esImagen = contentTypes.stream()
                                .anyMatch(ct -> ct != null && ct.toLowerCase(Locale.ROOT).startsWith("image/"));
                        if (esImagen) {
                            byte[] bytes = gpResp.body().asInputStream().readAllBytes();
                            if (bytes.length > 0) {
                                logoObtenido = bytes;
                                fuente = "GESTOPAGO";
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("getServiceLogo externo no disponible para idServicio={}: {}", idServicio, e.getMessage());
            }
        }

        // 3. Si GestoPago no devuelve imagen, intentar una única vez logo oficial de la marca
        if (logoObtenido == null) {
            logoObtenido = descargarLogoMarcaOficial(nombre);
            if (logoObtenido != null) {
                fuente = "MARCA_OFICIAL";
            }
        }

        // 4. Respaldo garantizado: PNG vectorial de color de marca generado en Java puro (sin java.awt)
        if (logoObtenido == null) {
            logoObtenido = generarLogoPng(nombre);
            fuente = "LOCAL_PNG";
        }

        logoCache.put(cacheKey, logoObtenido);

        // 5. Guardar en PostgreSQL para futuras consultas
        if (idServicio != null && idServicio > 0 && servicioLogoRepository != null) {
            try {
                ServicioLogoEntity entity = new ServicioLogoEntity();
                entity.setIdServicio(idServicio);
                entity.setNombreServicio(nombre.length() > 150 ? nombre.substring(0, 150) : nombre);
                entity.setContentType(detectarContentType(logoObtenido));
                entity.setLogoData(logoObtenido);
                entity.setFuente(fuente);
                servicioLogoRepository.save(entity);
            } catch (Exception e) {
                log.debug("No se pudo persistir logo en PostgreSQL para idServicio={}: {}", idServicio, e.getMessage());
            }
        }

        return logoObtenido;
    }

    private byte[] descargarLogoMarcaOficial(String nombreServicio) {
        String dominio = resolverDominioMarca(nombreServicio);
        if (dominio == null) {
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.google.com/s2/favicons?domain=" + dominio + "&sz=128"))
                    .timeout(Duration.ofSeconds(4))
                    .header("User-Agent", "GestoPagoCore/1.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = logoHttpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200 && response.body() != null && response.body().length > 100) {
                return response.body();
            }
        } catch (Exception e) {
            log.debug("No se pudo descargar logo oficial de {}: {}", dominio, e.getMessage());
        }
        return null;
    }

    private static String resolverDominioMarca(String nombreServicio) {
        String s = nombreServicio.toUpperCase(Locale.ROOT);
        Map<String, String> dominios = new LinkedHashMap<>();
        dominios.put("TELCEL", "telcel.com");
        dominios.put("MOVISTAR", "movistar.com.mx");
        dominios.put("AT&T", "att.com.mx");
        dominios.put("ATT", "att.com.mx");
        dominios.put("BAIT", "mibait.com");
        dominios.put("UNEFON", "unefon.com.mx");
        dominios.put("VIRGIN", "virginmobile.mx");
        dominios.put("PILLOFON", "pillofon.mx");
        dominios.put("CFE", "cfe.mx");
        dominios.put("TELMEX", "telmex.com");
        dominios.put("TOTALPLAY", "totalplay.com.mx");
        dominios.put("IZZI", "izzi.mx");
        dominios.put("MEGACABLE", "megacable.com.mx");
        dominios.put("SKY", "sky.com.mx");
        dominios.put("DISH", "dish.com.mx");
        dominios.put("NATURGY", "naturgy.com.mx");
        dominios.put("NETFLIX", "netflix.com");
        dominios.put("SPOTIFY", "spotify.com");
        dominios.put("AMAZON", "amazon.com.mx");
        dominios.put("UBER", "uber.com");
        dominios.put("DIDI", "didiglobal.com");
        dominios.put("XBOX", "xbox.com");
        dominios.put("PLAYSTATION", "playstation.com");
        dominios.put("NINTENDO", "nintendo.com");
        dominios.put("STEAM", "steampowered.com");
        dominios.put("CINEPOLIS", "cinepolis.com");
        dominios.put("CINEMEX", "cinemex.com");
        dominios.put("STARBUCKS", "starbucks.com.mx");
        dominios.put("INFONAVIT", "infonavit.org.mx");
        dominios.put("AVON", "avon.mx");
        dominios.put("NATURA", "natura.com.mx");
        dominios.put("JAFRA", "jafra.com.mx");
        dominios.put("FULLER", "fuller.com.mx");
        dominios.put("TUPPERWARE", "tupperware.com.mx");
        dominios.put("TELEVIA", "televia.com.mx");
        dominios.put("PASE", "pase.com.mx");
        dominios.put("SIAPA", "siapa.gob.mx");
        dominios.put("SACMEX", "sacmex.cdmx.gob.mx");
        dominios.put("AXTEL", "axtel.mx");
        dominios.put("BLUE TELECOMM", "bluetelecomm.mx");
        for (Map.Entry<String, String> entry : dominios.entrySet()) {
            if (s.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String detectarContentType(byte[] data) {
        if (data != null && data.length >= 4) {
            if ((data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
                return "image/png";
            }
            if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
                return "image/jpeg";
            }
        }
        return "image/svg+xml";
    }

    private String construirLogoUrl(Integer idServicio, String servicio) {
        int id = idServicio != null ? idServicio : 0;
        String srv = URLEncoder.encode(textoSeguro(servicio), StandardCharsets.UTF_8);
        return "/productos/getServiceLogo?idServicio=" + id + "&servicio=" + srv;
    }

    private static byte[] generarLogoPng(String nombreServicio) {
        int[] paleta = resolverPaletaMarca(nombreServicio);
        int size = 64;
        byte[] raw = new byte[size * (1 + size * 4)];
        int r1 = paleta[0];
        int g1 = paleta[1];
        int b1 = paleta[2];
        int r2 = paleta[3];
        int g2 = paleta[4];
        int b2 = paleta[5];

        int pos = 0;
        for (int y = 0; y < size; y++) {
            raw[pos++] = 0;
            for (int x = 0; x < size; x++) {
                boolean enEsquina = isCornerCutout(x, y, size, 14);
                if (enEsquina) {
                    raw[pos++] = 0;
                    raw[pos++] = 0;
                    raw[pos++] = 0;
                    raw[pos++] = 0;
                    continue;
                }
                double t = (x + y) / (double) ((size - 1) * 2);
                int r = (int) Math.round(r1 + (r2 - r1) * t);
                int g = (int) Math.round(g1 + (g2 - g1) * t);
                int b = (int) Math.round(b1 + (b2 - b1) * t);

                // Anillo interior suave para identidad visual
                double dx = x - 31.5;
                double dy = y - 31.5;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist >= 16.0 && dist <= 19.5) {
                    r = Math.min(255, r + 45);
                    g = Math.min(255, g + 45);
                    b = Math.min(255, b + 45);
                } else if (dist < 8.0) {
                    r = 255;
                    g = 255;
                    b = 255;
                }
                raw[pos++] = (byte) r;
                raw[pos++] = (byte) g;
                raw[pos++] = (byte) b;
                raw[pos++] = (byte) 255;
            }
        }

        try {
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
                deflater.write(raw);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(out);
            dos.write(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
            DataOutputStream ihdrDos = new DataOutputStream(ihdr);
            ihdrDos.writeInt(size);
            ihdrDos.writeInt(size);
            ihdrDos.writeByte(8);
            ihdrDos.writeByte(6);
            ihdrDos.writeByte(0);
            ihdrDos.writeByte(0);
            ihdrDos.writeByte(0);
            writePngChunk(dos, "IHDR", ihdr.toByteArray());
            writePngChunk(dos, "IDAT", compressed.toByteArray());
            writePngChunk(dos, "IEND", new byte[0]);
            return out.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private static boolean isCornerCutout(int x, int y, int size, int radius) {
        int rx = Math.min(x, size - 1 - x);
        int ry = Math.min(y, size - 1 - y);
        if (rx < radius && ry < radius) {
            int dx = radius - rx;
            int dy = radius - ry;
            return (dx * dx + dy * dy) > (radius * radius);
        }
        return false;
    }

    private static void writePngChunk(DataOutputStream dos, String type, byte[] data) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        dos.writeInt(data.length);
        dos.write(typeBytes);
        dos.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        dos.writeInt((int) crc.getValue());
    }

    private static int[] resolverPaletaMarca(String nombreServicio) {
        String upper = nombreServicio.toUpperCase(Locale.ROOT);
        if (upper.contains("TELCEL")) return new int[]{0, 51, 160, 0, 154, 222};
        if (upper.contains("MOVISTAR")) return new int[]{1, 157, 244, 91, 197, 0};
        if (upper.contains("AT&T") || upper.contains("ATT") || upper.contains("UNEFON")) {
            return new int[]{0, 87, 184, 0, 168, 224};
        }
        if (upper.contains("CFE")) return new int[]{0, 104, 71, 16, 185, 129};
        if (upper.contains("TELMEX")) return new int[]{0, 102, 178, 0, 174, 239};
        if (upper.contains("TOTALPLAY")) return new int[]{225, 29, 72, 249, 115, 22};
        if (upper.contains("IZZI")) return new int[]{236, 72, 153, 139, 92, 246};
        if (upper.contains("NETFLIX")) return new int[]{185, 28, 28, 239, 68, 68};
        if (upper.contains("SPOTIFY")) return new int[]{21, 128, 61, 29, 185, 84};
        if (upper.contains("AMAZON")) return new int[]{30, 41, 59, 245, 158, 11};
        if (upper.contains("XBOX")) return new int[]{16, 124, 16, 34, 197, 94};
        if (upper.contains("PLAYSTATION")) return new int[]{0, 55, 145, 0, 112, 209};
        if (upper.contains("AGUA") || upper.contains("SIAPA") || upper.contains("SACMEX")) {
            return new int[]{2, 132, 199, 6, 182, 212};
        }
        if (upper.contains("GAS") || upper.contains("NATURGY")) {
            return new int[]{220, 38, 38, 245, 158, 11};
        }
        return new int[]{0, 82, 204, 0, 143, 122};
    }

    private static String textoSeguro(String valor) {
        return valor == null ? "" : valor.trim();
    }
}
