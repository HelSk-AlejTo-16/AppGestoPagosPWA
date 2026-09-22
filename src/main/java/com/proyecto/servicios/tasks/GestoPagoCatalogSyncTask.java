package com.proyecto.servicios.tasks;

import com.proyecto.servicios.client.GestoPagoProductClient;
import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringReader;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
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

    // CRON: 0 0 0 * * ? -> Ejecutar a medianoche todos los días
   @Scheduled(cron = "${gestopago.sync.cron:0 0 0 * * ?}")
    @Transactional
    public void syncCatalog() {
        log.info("Iniciando sincronización del catálogo de GestoPago...");
        try {
            GestoPagoToken tokenEntity = tokenService.obtenerTokenActivo(idDistribuidor, codigoDispositivo)
                    .orElseThrow(() -> new RuntimeException("No hay token activo de GestoPago en BD."));
            String bearerToken = "Bearer " + tokenEntity.getToken();

            log.info("Llamando a la API de GestoPago getProductList...");
            String xmlResponse = productClient.getProductList(bearerToken);

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

            // 3.2 Filtro de Anomalías (Sanity Check)
            long totalActualMongo = mongoRepository.count();
            if (totalActualMongo > 0 && nuevosProductosSanos.size() < (totalActualMongo * 0.5)) {
                throw new SuspectedAnomalyException(
                        String.format("Anomalía detectada: La API devolvió %d productos, pero actualmente tenemos %d. " +
                                "La reducción es mayor al 50%%. Sincronización abortada por seguridad.", 
                                nuevosProductosSanos.size(), totalActualMongo));
            }

            log.info("Se procesarán {} productos sanos de {} descargados. Actualizando BD...", 
                    nuevosProductosSanos.size(), productosNuevosCrudos.size());

            procesarYGuardar(nuevosProductosSanos);

            log.info("Sincronización del catálogo de GestoPago finalizada con éxito.");

        } catch (SuspectedAnomalyException sae) {
            log.error("¡ALERTA DE ANOMALÍA! {}", sae.getMessage());
        } catch (Exception e) {
            log.error("Error crítico durante la sincronización del catálogo de GestoPago: {}", e.getMessage(), e);
        }
    }

    private void procesarYGuardar(List<GestoPagoProducto> nuevosProductos) {
        Set<Integer> nuevosIds = nuevosProductos.stream()
                .map(GestoPagoProducto::getIdProducto)
                .collect(Collectors.toSet());

        // Manejar PostgreSQL
        List<ProductoEntity> allPostgres = postgresRepository.findAll();
        for (ProductoEntity entity : allPostgres) {
            if (!nuevosIds.contains(entity.getIdProducto())) {
                entity.setActivo(false);
                postgresRepository.save(entity);
            }
        }

        // Manejar MongoDB
        List<ProductoDocument> allMongo = mongoRepository.findAll();
        for (ProductoDocument doc : allMongo) {
            if (!nuevosIds.contains(doc.getIdProducto())) {
                doc.setActivo(false);
                mongoRepository.save(doc);
            }
        }

        // Upsert
        for (GestoPagoProducto dto : nuevosProductos) {
            // Postgres
            ProductoEntity pgEntity = postgresRepository.findByIdProducto(dto.getIdProducto()).orElse(new ProductoEntity());
            pgEntity.setIdProducto(dto.getIdProducto());
            pgEntity.setIdServicio(dto.getIdServicio());
            pgEntity.setServicio(dto.getServicio());
            pgEntity.setProducto(dto.getProducto());
            pgEntity.setPrecio(dto.getPrecio());
            pgEntity.setLegend(dto.getLegend());
            pgEntity.setActivo(true);
            postgresRepository.save(pgEntity);

            // Mongo
            ProductoDocument mongoDoc = mongoRepository.findByIdProducto(dto.getIdProducto()).orElse(new ProductoDocument());
            mongoDoc.setIdProducto(dto.getIdProducto());
            mongoDoc.setIdServicio(dto.getIdServicio());
            mongoDoc.setServicio(dto.getServicio());
            mongoDoc.setProducto(dto.getProducto());
            mongoDoc.setPrecio(dto.getPrecio());
            mongoDoc.setLegend(dto.getLegend());
            mongoDoc.setActivo(true);
            mongoRepository.save(mongoDoc);
        }
    }
}
