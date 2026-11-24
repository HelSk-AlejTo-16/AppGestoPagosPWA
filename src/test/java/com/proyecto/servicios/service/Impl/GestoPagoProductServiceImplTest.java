package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.client.GestoPagoProductClient;
import com.proyecto.servicios.entity.gestopago.ProductoDocument;
import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import com.proyecto.servicios.exception.CatalogUnavailableException;
import com.proyecto.servicios.model.gestopago.GestoPagoProductResponse;
import com.proyecto.servicios.model.gestopago.ProductoBusquedaRequest;
import com.proyecto.servicios.repositorys.gestopago.ProductoMongoRepository;
import com.proyecto.servicios.repositorys.gestopago.ProductoPostgresRepository;
import com.proyecto.servicios.service.GestoPagoTokenService;
import com.proyecto.servicios.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

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
    // Escenario 1: PostgreSQL responde (fuente primaria)
    // ============================

    @Test
    @DisplayName("Debe retornar productos desde PostgreSQL como fuente primaria cuando hay datos disponibles")
    void obtenerProductos_desdePostgresExitoso() {
        // Arrange
        List<ProductoEntity> pgEntities = List.of(
                crearEntidadPostgres(1, "Producto A", 100.0, true),
                crearEntidadPostgres(2, "Producto B", 200.0, true),
                crearEntidadPostgres(3, "Producto Inactivo", 50.0, false)
        );
        when(postgresRepository.findAll()).thenReturn(pgEntities);

        // Act
        GestoPagoProductResponse response = productService.obtenerProductos();

        // Assert
        assertNotNull(response);
        assertEquals("00", response.getMensaje().getCodigo());
        assertEquals("Catálogo obtenido de PostgreSQL local", response.getMensaje().getTexto());
        assertEquals(2, response.getProductos().size());
        verify(mongoRepository, never()).findAll();
        verify(productClient, never()).getProductList(anyString());
        verify(notificationService, never()).alertFallbackTriggered(anyString(), anyString());
    }

    // ============================
    // Escenario 2: Fallback a MongoDB (reserva)
    // ============================

    @Test
    @DisplayName("Debe usar MongoDB como respaldo cuando PostgreSQL está vacío")
    void obtenerProductos_fallbackMongo_postgresVacio() {
        // Arrange - PostgreSQL vacío
        when(postgresRepository.findAll()).thenReturn(Collections.emptyList());
        // MongoDB con datos
        List<ProductoDocument> mongoDocs = List.of(
                crearDocumentoMongo(1, "Producto Mongo", 150.0, true)
        );
        when(mongoRepository.findAll()).thenReturn(mongoDocs);

        // Act
        GestoPagoProductResponse response = productService.obtenerProductos();

        // Assert
        assertNotNull(response);
        assertEquals("00", response.getMensaje().getCodigo());
        assertEquals("Catálogo obtenido de MongoDB (Respaldo)", response.getMensaje().getTexto());
        assertEquals(1, response.getProductos().size());
        verify(notificationService).alertFallbackTriggered(eq("MongoDB"), anyString());
    }

    @Test
    @DisplayName("Debe usar MongoDB como respaldo cuando PostgreSQL lanza una excepción")
    void obtenerProductos_fallbackMongo_postgresExcepcion() {
        // Arrange - PostgreSQL falla
        when(postgresRepository.findAll()).thenThrow(new RuntimeException("Connection refused"));
        // MongoDB con datos
        List<ProductoDocument> mongoDocs = List.of(
                crearDocumentoMongo(5, "Producto Respaldo", 300.0, true)
        );
        when(mongoRepository.findAll()).thenReturn(mongoDocs);

        // Act
        GestoPagoProductResponse response = productService.obtenerProductos();

        // Assert
        assertNotNull(response);
        assertEquals("00", response.getMensaje().getCodigo());
        assertEquals(1, response.getProductos().size());
        assertEquals(300.0, response.getProductos().get(0).getPrecio());
        verify(notificationService).alertFallbackTriggered(eq("MongoDB"), contains("Connection refused"));
    }

    // ============================
    // Escenario 3: Fallback a API Externa
    // ============================

    @Test
    @DisplayName("Debe consultar la API externa cuando PostgreSQL y MongoDB fallan")
    void obtenerProductos_fallbackApiExterna() {
        // Arrange - Todo falla localmente
        when(postgresRepository.findAll()).thenReturn(Collections.emptyList());
        when(mongoRepository.findAll()).thenReturn(Collections.emptyList());

        when(tokenService.obtenerBearerToken(any(), any())).thenReturn("token-fresco-123");

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
        verify(notificationService, times(2)).alertFallbackTriggered(anyString(), anyString());
    }

    // ============================
    // Escenario 4: Fallo Total (503)
    // ============================

    @Test
    @DisplayName("Debe lanzar CatalogUnavailableException cuando todas las fuentes fallan")
    void obtenerProductos_falloTotal_lanzaExcepcion() {
        // Arrange - TODO falla
        when(postgresRepository.findAll()).thenReturn(Collections.emptyList());
        when(mongoRepository.findAll()).thenReturn(Collections.emptyList());
        when(tokenService.obtenerBearerToken(any(), any()))
                .thenThrow(new RuntimeException("Fallo en servicio de autenticación"));

        // Act & Assert
        CatalogUnavailableException exception = assertThrows(
                CatalogUnavailableException.class,
                () -> productService.obtenerProductos()
        );
        assertTrue(exception.getMessage().contains("no está disponible temporalmente"));
    }

    // ============================
    // Escenario 5: Filtrado de inactivos
    // ============================

    @Test
    @DisplayName("Debe filtrar productos inactivos y retornar solo los activos de PostgreSQL")
    void obtenerProductos_filtraInactivos() {
        // Arrange - 3 productos, solo 1 activo
        List<ProductoEntity> pgEntities = List.of(
                crearEntidadPostgres(1, "Activo", 100.0, true),
                crearEntidadPostgres(2, "Inactivo 1", 200.0, false),
                crearEntidadPostgres(3, "Inactivo 2", 300.0, false)
        );
        when(postgresRepository.findAll()).thenReturn(pgEntities);

        // Act
        GestoPagoProductResponse response = productService.obtenerProductos();

        // Assert
        assertEquals(1, response.getProductos().size());
        assertEquals("Activo", response.getProductos().get(0).getProducto());
    }

    @Test
    @DisplayName("Debe paginar productos y agrupar/paginar servicios correctamente")
    void buscarProductosYServiciosPaginados_retornaSubconjuntosCorrectos() {
        ProductoEntity p1 = crearEntidadPostgres(1, "Recarga $20", 20.0, true);
        p1.setServicio("Telcel");
        p1.setTipoFront(1);
        ProductoEntity p2 = crearEntidadPostgres(2, "Recarga $50", 50.0, true);
        p2.setServicio("Telcel");
        p2.setTipoFront(1);
        ProductoEntity p3 = crearEntidadPostgres(3, "Pago CFE Luz", 0.0, true);
        p3.setIdServicio(200);
        p3.setServicio("CFE");
        p3.setTipoFront(2);

        when(postgresRepository.findAll()).thenReturn(List.of(p1, p2, p3));

        var pagProductos = productService.buscarProductosPaginados(
                new ProductoBusquedaRequest(0, 2, null, null, null));
        assertEquals(3, pagProductos.totalElementos());
        assertEquals(2, pagProductos.totalPaginas());
        assertEquals(2, pagProductos.contenido().size());
        assertTrue(pagProductos.primera());
        assertFalse(pagProductos.ultima());

        var pagServicios = productService.buscarServiciosPaginados(
                new ProductoBusquedaRequest(0, 1, null, null, null));
        assertEquals(2, pagServicios.totalElementos());
        assertEquals(2, pagServicios.totalPaginas());
        assertEquals(1, pagServicios.contenido().size());
        assertEquals("CFE", pagServicios.contenido().get(0).servicio());
    }
}
