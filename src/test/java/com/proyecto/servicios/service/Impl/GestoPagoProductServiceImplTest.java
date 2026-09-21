package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.GestoPagoProductClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import com.proyecto.servicios.entity.gestopago.ProductoDocument;
import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import com.proyecto.servicios.exception.CatalogUnavailableException;
import com.proyecto.servicios.exception.GestoPagoAuthException;
import com.proyecto.servicios.model.gestopago.GestoPagoProductResponse;
import com.proyecto.servicios.repositorys.gestopago.ProductoMongoRepository;
import com.proyecto.servicios.repositorys.gestopago.ProductoPostgresRepository;
import com.proyecto.servicios.service.GestoPagoTokenService;
import com.proyecto.servicios.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GestoPagoProductServiceImplTest {

    @Mock
    private GestoPagoProductClient productClient;

    @Mock
    private GestoPagoTokenService tokenService;

    @Mock
    private ProductoMongoRepository mongoRepository;

    @Mock
    private ProductoPostgresRepository postgresRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private GestoPagoProductServiceImpl productService;

    private ProductoDocument crearDocumentoMongo(Integer idProducto, String nombre, Double precio, Boolean activo) {
        ProductoDocument doc = new ProductoDocument();
        doc.setIdProducto(idProducto);
        doc.setIdServicio(100);
        doc.setServicio("TEST");
        doc.setProducto(nombre);
        doc.setPrecio(precio);
        doc.setLegend("Leyenda de prueba");
        doc.setActivo(activo);
        return doc;
    }

    private ProductoEntity crearEntidadPostgres(Integer idProducto, String nombre, Double precio, Boolean activo) {
        ProductoEntity entity = new ProductoEntity();
        entity.setIdProducto(idProducto);
        entity.setIdServicio(100);
        entity.setServicio("TEST");
        entity.setProducto(nombre);
        entity.setPrecio(precio);
        entity.setLegend("Leyenda de prueba");
        entity.setActivo(activo);
        return entity;
    }

    // ============================
    // Escenario 1: MongoDB responde
    // ============================

    @Test
    @DisplayName("Debe retornar productos desde MongoDB cuando hay datos disponibles")
    void obtenerProductos_desdeMongoExitoso() {
        // Arrange
        List<ProductoDocument> mongoDocs = List.of(
                crearDocumentoMongo(1, "Producto A", 100.0, true),
                crearDocumentoMongo(2, "Producto B", 200.0, true),
                crearDocumentoMongo(3, "Producto Inactivo", 50.0, false)
        );
        when(mongoRepository.findAll()).thenReturn(mongoDocs);

        // Act
        GestoPagoProductResponse response = productService.obtenerProductos();

        // Assert
        assertNotNull(response);
        assertEquals("00", response.getMensaje().getCodigo());
        assertEquals("Catálogo obtenido de MongoDB local", response.getMensaje().getTexto());
        // Solo 2 productos activos (el inactivo se filtra)
        assertEquals(2, response.getProductos().size());
        // No se debe llamar a Postgres ni a la API
        verify(postgresRepository, never()).findAll();
        verify(productClient, never()).getProductList(anyString());
        verify(notificationService, never()).alertFallbackTriggered(anyString(), anyString());
    }

    // ============================
    // Escenario 2: Fallback a PostgreSQL
    // ============================

    @Test
    @DisplayName("Debe usar PostgreSQL como respaldo cuando MongoDB está vacío")
    void obtenerProductos_fallbackPostgres_mongoVacio() {
        // Arrange - MongoDB vacío
        when(mongoRepository.findAll()).thenReturn(Collections.emptyList());
        // PostgreSQL con datos
        List<ProductoEntity> pgEntities = List.of(
                crearEntidadPostgres(1, "Producto PG", 150.0, true)
        );
        when(postgresRepository.findAll()).thenReturn(pgEntities);

        // Act
        GestoPagoProductResponse response = productService.obtenerProductos();

        // Assert
        assertNotNull(response);
        assertEquals("00", response.getMensaje().getCodigo());
        assertEquals("Catálogo obtenido de PostgreSQL (Respaldo)", response.getMensaje().getTexto());
        assertEquals(1, response.getProductos().size());
        // Se debe notificar el fallback
        verify(notificationService).alertFallbackTriggered(eq("PostgreSQL"), anyString());
    }

    @Test
    @DisplayName("Debe usar PostgreSQL como respaldo cuando MongoDB lanza una excepción")
    void obtenerProductos_fallbackPostgres_mongoExcepcion() {
        // Arrange - MongoDB explota
        when(mongoRepository.findAll()).thenThrow(new RuntimeException("Connection refused"));
        // PostgreSQL con datos
        List<ProductoEntity> pgEntities = List.of(
                crearEntidadPostgres(5, "Producto Respaldo", 300.0, true)
        );
        when(postgresRepository.findAll()).thenReturn(pgEntities);

        // Act
        GestoPagoProductResponse response = productService.obtenerProductos();

        // Assert
        assertNotNull(response);
        assertEquals("00", response.getMensaje().getCodigo());
        assertEquals(1, response.getProductos().size());
        assertEquals(300.0, response.getProductos().get(0).getPrecio());
        verify(notificationService).alertFallbackTriggered(eq("PostgreSQL"), contains("Connection refused"));
    }

    // ============================
    // Escenario 3: Fallback a API Externa
    // ============================

    @Test
    @DisplayName("Debe consultar la API externa cuando MongoDB y PostgreSQL fallan")
    void obtenerProductos_fallbackApiExterna() {
        // Arrange - Todo falla localmente
        when(mongoRepository.findAll()).thenReturn(Collections.emptyList());
        when(postgresRepository.findAll()).thenReturn(Collections.emptyList());

        // Simular token activo
        GestoPagoToken token = new GestoPagoToken();
        token.setToken("token-fresco-123");
               when(tokenService.obtenerBearerToken(any(), any())).thenReturn("Bearer token-fresco-123");

        // Simular respuesta XML de la API
        String xmlResponse = "<?xml version='1.0' encoding='UTF-8'?>" +
                "<RESPONSE><MENSAJE><CODIGO>01</CODIGO><TEXTO>OK</TEXTO></MENSAJE>" +
                "<PRODUCTOS><producto servicio='TEST' producto='API Producto' idServicio='1' idProducto='99' " +
                "idCatTipoServicio='1' tipoFront='1' hasDigitoVerificador='false' precio='500.0' " +
                "showAyuda='false' tipoReferencia='a'><legend>Leyenda API</legend></producto></PRODUCTOS></RESPONSE>";
        when(productClient.getProductList(any())).thenReturn(xmlResponse);

        // Act
        GestoPagoProductResponse response = productService.obtenerProductos();

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getProductos().size());
        assertTrue(response.getMensaje().getTexto().contains("API Externa Directa"));
        // Se deben disparar las dos notificaciones de fallback
        verify(notificationService, times(2)).alertFallbackTriggered(anyString(), anyString());
    }

    // ============================
    // Escenario 4: Fallo Total (503)
    // ============================

@Test
    @DisplayName("Debe lanzar GestoPagoAuthException cuando todas las fuentes fallan y no hay token")
    void obtenerProductos_falloTotal_lanzaExcepcion() {
        // Arrange - TODO falla
        when(mongoRepository.findAll()).thenReturn(Collections.emptyList());
        when(postgresRepository.findAll()).thenReturn(Collections.emptyList());
        when(tokenService.obtenerBearerToken(any(), any()))
                .thenThrow(new IllegalStateException("sin token"));

        // Act & Assert
        GestoPagoAuthException exception = assertThrows(
                GestoPagoAuthException.class,
                () -> productService.obtenerProductos()
        );
        assertTrue(exception.getMessage().contains("No hay token disponible"));
    }
    // ============================
    // Escenario 5: Filtrado de inactivos
    // ============================

    @Test
    @DisplayName("Debe filtrar productos inactivos y retornar solo los activos de MongoDB")
    void obtenerProductos_filtraInactivos() {
        // Arrange - 3 productos, solo 1 activo
        List<ProductoDocument> mongoDocs = List.of(
                crearDocumentoMongo(1, "Activo", 100.0, true),
                crearDocumentoMongo(2, "Inactivo 1", 200.0, false),
                crearDocumentoMongo(3, "Inactivo 2", 300.0, false)
        );
        when(mongoRepository.findAll()).thenReturn(mongoDocs);

        // Act
        GestoPagoProductResponse response = productService.obtenerProductos();

        // Assert
        assertEquals(1, response.getProductos().size());
        assertEquals("Activo", response.getProductos().get(0).getProducto());
    }

        @Test
    @DisplayName("Debe lanzar CatalogUnavailableException ante un error inesperado en la API")
    void obtenerProductos_errorInesperado_lanzaCatalogUnavailable() {
        when(mongoRepository.findAll()).thenReturn(Collections.emptyList());
        when(postgresRepository.findAll()).thenReturn(Collections.emptyList());
        when(tokenService.obtenerBearerToken(any(), any())).thenReturn("Bearer token-123");
        when(productClient.getProductList(any())).thenThrow(new RuntimeException("fallo inesperado"));

        CatalogUnavailableException exception = assertThrows(
                CatalogUnavailableException.class,
                () -> productService.obtenerProductos()
        );
        assertTrue(exception.getMessage().contains("no está disponible temporalmente"));
    }
}
