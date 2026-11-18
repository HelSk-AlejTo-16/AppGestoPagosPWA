package com.proyecto.servicios.tasks;

import com.proyecto.servicios.client.GestoPagoProductClient;
import com.proyecto.servicios.entity.gestopago.ProductoDocument;
import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import com.proyecto.servicios.repositorys.gestopago.ProductoMongoRepository;
import com.proyecto.servicios.repositorys.gestopago.ProductoPostgresRepository;
import com.proyecto.servicios.service.GestoPagoTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GestoPagoCatalogSyncTaskTest {

    @Mock
    private GestoPagoProductClient productClient;

    @Mock
    private GestoPagoTokenService tokenService;

    @Mock
    private ProductoMongoRepository mongoRepository;

    @Mock
    private ProductoPostgresRepository postgresRepository;

    @InjectMocks
    private GestoPagoCatalogSyncTask syncTask;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(syncTask, "idDistribuidor", 83);
        ReflectionTestUtils.setField(syncTask, "codigoDispositivo", "GPS83-TPV-17");
    }

    private String crearXmlConProductos(int cantidad) {
        StringBuilder sb = new StringBuilder("<?xml version='1.0' encoding='UTF-8'?><RESPONSE>");
        sb.append("<MENSAJE><CODIGO>01</CODIGO><TEXTO>OK</TEXTO></MENSAJE><PRODUCTOS>");
        for (int i = 1; i <= cantidad; i++) {
            sb.append(String.format(
                    "<producto servicio='SVC' producto='Prod %d' idServicio='1' idProducto='%d' " +
                    "idCatTipoServicio='1' tipoFront='1' hasDigitoVerificador='false' precio='%d.0' " +
                    "showAyuda='false' tipoReferencia='a'><legend>Leyenda</legend></producto>",
                    i, i, i * 100));
        }
        sb.append("</PRODUCTOS></RESPONSE>");
        return sb.toString();
    }

    private static int contarElementos(Iterable<?> iterable) {
        if (iterable instanceof Collection<?> collection) {
            return collection.size();
        }
        int total = 0;
        for (Object ignored : iterable) {
            total++;
        }
        return total;
    }

    @Test
    @DisplayName("Debe sincronizar productos correctamente en lote cuando la API responde con datos válidos")
    void syncCatalog_exitoso() {
        // Arrange
        when(tokenService.obtenerBearerToken(anyInt(), any())).thenReturn("valid-token");
        when(productClient.getProductList(any())).thenReturn(crearXmlConProductos(3));
        when(postgresRepository.count()).thenReturn(0L);
        when(mongoRepository.count()).thenReturn(0L);
        when(mongoRepository.findAll()).thenReturn(Collections.emptyList());
        when(postgresRepository.findAll()).thenReturn(Collections.emptyList());

        // Act
        syncTask.syncCatalog();

        // Assert - Se deben guardar 3 productos en lote en cada BD sin consultas 1x1
        verify(postgresRepository, times(1)).saveAll(argThat(items -> contarElementos(items) == 3));
        verify(mongoRepository, times(1)).saveAll(argThat(items -> contarElementos(items) == 3));
        verify(postgresRepository, never()).findByIdProducto(any());
        verify(mongoRepository, never()).findByIdProducto(any());
    }

    @Test
    @DisplayName("No debe guardar nada si la API devuelve una lista vacía")
    void syncCatalog_listaVacia_noGuardaNada() {
        // Arrange
        when(tokenService.obtenerBearerToken(anyInt(), any())).thenReturn("valid-token");
        String xmlVacio = "<?xml version='1.0' encoding='UTF-8'?><RESPONSE>" +
                "<MENSAJE><CODIGO>01</CODIGO><TEXTO>OK</TEXTO></MENSAJE><PRODUCTOS></PRODUCTOS></RESPONSE>";
        when(productClient.getProductList(any())).thenReturn(xmlVacio);

        // Act
        syncTask.syncCatalog();

        // Assert - No se debe guardar nada
        verify(postgresRepository, never()).saveAll(any());
        verify(mongoRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Debe abortar por anomalía si el catálogo nuevo es menor al 50% del existente")
    void syncCatalog_anomaliaDetectada_abortaSincronizacion() {
        // Arrange
        when(tokenService.obtenerBearerToken(anyInt(), any())).thenReturn("valid-token");
        // API devuelve solo 2 productos
        when(productClient.getProductList(any())).thenReturn(crearXmlConProductos(2));
        // Ya teníamos 100 en Postgres
        when(postgresRepository.count()).thenReturn(100L);

        // Act
        syncTask.syncCatalog();

        // Assert - No se debe actualizar la base de datos
        verify(postgresRepository, never()).saveAll(any());
        verify(mongoRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("No debe romper si no hay token disponible")
    void syncCatalog_sinToken_noExplota() {
        // Arrange
        when(tokenService.obtenerBearerToken(anyInt(), any())).thenReturn(null);

        // Act - No debe lanzar excepción
        syncTask.syncCatalog();

        // Assert - No se debe guardar nada
        verify(postgresRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("No debe romper si la API lanza una excepción de red (Feign)")
    void syncCatalog_errorDeRed_noExplota() {
        // Arrange
        when(tokenService.obtenerBearerToken(anyInt(), any())).thenReturn("valid-token");
        when(productClient.getProductList(any())).thenThrow(new RuntimeException("Connection timed out"));
        when(postgresRepository.count()).thenReturn(100L);

        // Act - No debe lanzar excepción
        syncTask.syncCatalog();

        // Assert
        verify(postgresRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Debe cargar respaldo local productos.xml al iniciar si la API devuelve XML vacío y la BD está en cero")
    void verificarCatalogoInicial_cuandoApiDevuelveVacioYCeroEnBd_cargaRespaldoLocal() {
        // Arrange
        when(postgresRepository.count()).thenReturn(0L);
        when(tokenService.obtenerBearerToken(anyInt(), any())).thenReturn("expired-token");
        String xmlTokenExpirado = "<?xml version='1.0' encoding='UTF-8'?><RESPONSE>" +
                "<MENSAJE><CODIGO>00</CODIGO><TEXTO>Token expirado</TEXTO></MENSAJE></RESPONSE>";
        when(productClient.getProductList(any())).thenReturn(xmlTokenExpirado);
        when(postgresRepository.findAll()).thenReturn(Collections.emptyList());
        when(mongoRepository.findAll()).thenReturn(Collections.emptyList());

        // Act
        syncTask.verificarCatalogoInicial();

        // Assert - Carga los productos de productos.xml en lote
        verify(postgresRepository, times(1)).saveAll(argThat(items -> contarElementos(items) > 100));
        verify(mongoRepository, times(1)).saveAll(argThat(items -> contarElementos(items) > 100));
    }
}
