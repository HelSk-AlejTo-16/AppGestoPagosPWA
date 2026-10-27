package com.proyecto.servicios.tasks;

import com.proyecto.servicios.client.GestoPagoProductClient;
import com.proyecto.servicios.entity.gestopago.ProductoDocument;
import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import com.proyecto.servicios.exception.SuspectedAnomalyException;
import com.proyecto.servicios.model.gestopago.GestoPagoProductResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoProducto;
import com.proyecto.servicios.repositorys.gestopago.ProductoMongoRepository;
import com.proyecto.servicios.repositorys.gestopago.ProductoPostgresRepository;
import com.proyecto.servicios.service.GestoPagoTokenService;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "gestopago.sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GestoPagoCatalogSyncTask {

    private final GestoPagoProductClient productClient;
    private final GestoPagoTokenService tokenService;
    private final ProductoMongoRepository mongoRepository;
    private final ProductoPostgresRepository postgresRepository;

    @Value("${gestopago.auth.id-distribuidor}")
    private Integer idDistribuidor;

    @Value("${gestopago.auth.codigo-dispositivo}")
    private String codigoDispositivo;

    public GestoPagoCatalogSyncTask(GestoPagoProductClient productClient,
                                    GestoPagoTokenService tokenService,
                                    ProductoMongoRepository mongoRepository,
                                    ProductoPostgresRepository postgresRepository) {
        this.productClient = productClient;
        this.tokenService = tokenService;
        this.mongoRepository = mongoRepository;
        this.postgresRepository = postgresRepository;
    }

    /**
     * Si la base de datos arranca vacía (ej. primer despliegue en Render),
     * dispara inmediatamente la sincronización sin esperar a la medianoche del CRON.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void verificarCatalogoInicial() {
        try {
            long totalPostgres = postgresRepository.count();
            if (totalPostgres == 0) {
                log.info("Catálogo en PostgreSQL vacío al iniciar (0 productos). Disparando sincronización inmediata...");
                syncCatalog();
            } else {
                log.info("Catálogo en PostgreSQL listo con {} productos.", totalPostgres);
            }
        } catch (Exception e) {
            log.warn("No se pudo verificar el catálogo inicial al arrancar ({})", e.getClass().getSimpleName());
        }
    }

    // CRON: Ejecutar automáticamente según la programación configurada
    @Scheduled(cron = "${gestopago.sync.cron:0 0 0 * * ?}")
    @Transactional
    public void syncCatalog() {
        log.info("Iniciando sincronización del catálogo de GestoPago...");
        try {
            String xmlResponse = obtenerXmlCatalogo();

            JAXBContext jaxbContext = JAXBContext.newInstance(GestoPagoProductResponse.class);
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            GestoPagoProductResponse response = (GestoPagoProductResponse) unmarshaller.unmarshal(new StringReader(xmlResponse));

            if (response == null || response.getProductos() == null || response.getProductos().isEmpty()) {
                throw new RuntimeException("El catálogo devuelto está vacío o hubo un error en la respuesta XML. No se sobrescribirá la base de datos.");
            }

            List<GestoPagoProducto> productosNuevosCrudos = response.getProductos();

            // 3.1 Validación de Integridad (Filtrar corruptos)
            List<GestoPagoProducto> nuevosProductosSanos = productosNuevosCrudos.stream()
                    .filter(p -> {
                        if (p.getIdProducto() == null) {
                            log.warn("Producto ignorado: Falta ID");
                            return false;
                        }
                        if (p.getPrecio() == null || p.getPrecio() < 0) {
                            log.warn("Producto ignorado: Precio inválido para ID {}", p.getIdProducto());
                            return false;
                        }
                        return true;
                    })
                    .collect(Collectors.toList());

            // 3.2 Filtro de Anomalías (Sanity Check basado en PostgreSQL / MongoDB)
            long totalActual = obtenerConteoActualSeguro();
            if (totalActual > 0 && nuevosProductosSanos.size() < (totalActual * 0.5)) {
                throw new SuspectedAnomalyException(
                        String.format("Anomalía detectada: La API devolvió %d productos, pero actualmente tenemos %d. " +
                                "La reducción es mayor al 50%%. Sincronización abortada por seguridad.",
                                nuevosProductosSanos.size(), totalActual));
            }

            log.info("Se procesarán {} productos sanos de {} descargados. Actualizando BD...",
                    nuevosProductosSanos.size(), productosNuevosCrudos.size());

            procesarYGuardar(nuevosProductosSanos);

            log.info("Sincronización del catálogo de GestoPago finalizada con éxito.");

        } catch (SuspectedAnomalyException sae) {
            log.error("¡ALERTA DE ANOMALÍA! {}", sae.getMessage());
        } catch (Exception e) {
            log.error("Falló la sincronización del catálogo GestoPago ({})", e.getClass().getSimpleName());
        }
    }

    private String obtenerXmlCatalogo() throws Exception {
        try {
            String bearerToken = tokenService.obtenerBearerToken(idDistribuidor, codigoDispositivo);
            log.info("Llamando a la API de GestoPago getProductList...");
            return productClient.getProductList(bearerToken);
        } catch (Exception apiEx) {
            log.warn("La llamada directa a getProductList de GestoPago no estuvo disponible ({}).", apiEx.getClass().getSimpleName());
            if (postgresRepository.count() == 0) {
                Path backupPath = Path.of("productos.xml");
                if (Files.exists(backupPath)) {
                    log.info("Cargando catálogo inicial de respaldo desde productos.xml para evitar base de datos vacía...");
                    return Files.readString(backupPath, StandardCharsets.UTF_8);
                }
            }
            throw apiEx;
        }
    }

    private long obtenerConteoActualSeguro() {
        try {
            long countPg = postgresRepository.count();
            if (countPg > 0) {
                return countPg;
            }
            return mongoRepository.count();
        } catch (Exception e) {
            return postgresRepository.count();
        }
    }

    private void procesarYGuardar(List<GestoPagoProducto> nuevosProductos) {
        Set<Integer> nuevosIds = nuevosProductos.stream()
                .map(GestoPagoProducto::getIdProducto)
                .collect(Collectors.toSet());

        // 1. Manejar PostgreSQL (Fuente transaccional primaria para pagos)
        List<ProductoEntity> allPostgres = postgresRepository.findAll();
        for (ProductoEntity entity : allPostgres) {
            if (!nuevosIds.contains(entity.getIdProducto())) {
                entity.setActivo(false);
                postgresRepository.save(entity);
            }
        }

        for (GestoPagoProducto dto : nuevosProductos) {
            ProductoEntity pgEntity = postgresRepository.findByIdProducto(dto.getIdProducto()).orElse(new ProductoEntity());
            pgEntity.setIdProducto(dto.getIdProducto());
            pgEntity.setIdServicio(dto.getIdServicio());
            pgEntity.setServicio(dto.getServicio());
            pgEntity.setProducto(dto.getProducto());
            pgEntity.setPrecio(dto.getPrecio());
            pgEntity.setTipoFront(dto.getTipoFront());
            pgEntity.setTipoReferencia(dto.getTipoReferencia());
            pgEntity.setHasDigitoVerificador(dto.getHasDigitoVerificador() != null && dto.getHasDigitoVerificador());
            pgEntity.setLegend(dto.getLegend());
            pgEntity.setActivo(true);
            postgresRepository.save(pgEntity);
        }

        // 2. Manejar MongoDB de forma aislada (si Mongo no está disponible, no interrumpe PostgreSQL)
        try {
            List<ProductoDocument> allMongo = mongoRepository.findAll();
            for (ProductoDocument doc : allMongo) {
                if (!nuevosIds.contains(doc.getIdProducto())) {
                    doc.setActivo(false);
                    mongoRepository.save(doc);
                }
            }

            for (GestoPagoProducto dto : nuevosProductos) {
                ProductoDocument mongoDoc = mongoRepository.findByIdProducto(dto.getIdProducto()).orElse(new ProductoDocument());
                mongoDoc.setIdProducto(dto.getIdProducto());
                mongoDoc.setIdServicio(dto.getIdServicio());
                mongoDoc.setServicio(dto.getServicio());
                mongoDoc.setProducto(dto.getProducto());
                mongoDoc.setPrecio(dto.getPrecio());
                mongoDoc.setTipoFront(dto.getTipoFront());
                mongoDoc.setTipoReferencia(dto.getTipoReferencia());
                mongoDoc.setHasDigitoVerificador(dto.getHasDigitoVerificador() != null && dto.getHasDigitoVerificador());
                mongoDoc.setLegend(dto.getLegend());
                mongoDoc.setActivo(true);
                mongoRepository.save(mongoDoc);
            }
        } catch (Exception mongoEx) {
            log.warn("Catálogo sincronizado en PostgreSQL; MongoDB no estuvo disponible en este ciclo ({}).",
                    mongoEx.getClass().getSimpleName());
        }
    }
}
