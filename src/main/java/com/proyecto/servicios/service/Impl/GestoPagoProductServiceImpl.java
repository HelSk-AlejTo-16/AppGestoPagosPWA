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
import com.proyecto.servicios.model.gestopago.GestoPagoMensaje;
import com.proyecto.servicios.model.gestopago.GestoPagoProductResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoProducto;
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
import java.util.List;

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

    // ============================
    // Cadena de respaldo: MongoDB, PostgreSQL y API directa
    // ============================

    private GestoPagoProductResponse consultarCatalogo() {
        try {
            return desdeMongo();
        } catch (Exception eMongo) {
            notificationService.alertFallbackTriggered("PostgreSQL",
                    "Fallo al obtener datos de MongoDB: " + eMongo.getMessage());
        }

        try {
            return desdePostgres();
        } catch (Exception ePg) {
            notificationService.alertFallbackTriggered("API Directa GestoPago",
                    "Fallo de base de datos Postgres: " + ePg.getMessage());
        }

        return desdeApiExterna();
    }

    private GestoPagoProductResponse desdeMongo() {
        List<ProductoDocument> docs = mongoRepository.findAll();
        if (docs.isEmpty()) {
            throw new IllegalStateException("MongoDB vacío");
        }
        log.info("Catálogo obtenido exitosamente desde MongoDB");
        List<GestoPagoProducto> productos = docs.stream()
                .filter(d -> Boolean.TRUE.equals(d.getActivo()))
                .map(this::mapMongoToDto)
                .toList();
        return armarRespuesta("Catálogo obtenido de MongoDB local", productos);
    }

    private GestoPagoProductResponse desdePostgres() {
        List<ProductoEntity> entidades = postgresRepository.findAll();
        if (entidades.isEmpty()) {
            throw new IllegalStateException("PostgreSQL vacío");
        }
        log.info("Catálogo obtenido exitosamente desde PostgreSQL (Respaldo)");
        List<GestoPagoProducto> productos = entidades.stream()
                .filter(e -> Boolean.TRUE.equals(e.getActivo()))
                .map(this::mapPostgresToDto)
                .toList();
        return armarRespuesta("Catálogo obtenido de PostgreSQL (Respaldo)", productos);
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
            log.error("Fallo crítico inesperado al consultar GestoPago", e);
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
        p.setLegend(entity.getLegend());
        return p;
    }
}