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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "gestopago.sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GestoPagoCatalogSyncTask {

    private static final long MIN_PRODUCTOS_CATALOGO_COMPLETO = 50L;

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
            if (totalPostgres < MIN_PRODUCTOS_CATALOGO_COMPLETO) {
                log.info("Catálogo en PostgreSQL incompleto al iniciar ({} productos). Disparando sincronización inmediata...", totalPostgres);
                syncCatalog();
                if (postgresRepository.count() < MIN_PRODUCTOS_CATALOGO_COMPLETO) {
                    cargarCatalogoDesdeRespaldoLocal();
                }
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
            List<GestoPagoProducto> nuevosProductosSanos = parsearYFiltrarProductos(xmlResponse);

            // 3.2 Filtro de Anomalías (Sanity Check basado en PostgreSQL / MongoDB)
            long totalActual = obtenerConteoActualSeguro();
            if (totalActual > 0 && nuevosProductosSanos.size() < (totalActual * 0.5)) {
                throw new SuspectedAnomalyException(
                        String.format("Anomalía detectada: La API devolvió %d productos, pero actualmente tenemos %d. " +
                                "La reducción es mayor al 50%%. Sincronización abortada por seguridad.",
                                nuevosProductosSanos.size(), totalActual));
            }

            log.info("Se procesarán {} productos sanos. Actualizando BD...", nuevosProductosSanos.size());

            procesarYGuardar(nuevosProductosSanos);

            log.info("Sincronización del catálogo de GestoPago finalizada con éxito.");

        } catch (SuspectedAnomalyException sae) {
            log.error("¡ALERTA DE ANOMALÍA! {}", sae.getMessage());
        } catch (Exception e) {
            log.error("Falló la sincronización del catálogo GestoPago ({})", e.getClass().getSimpleName());
        }
    }

    private void cargarCatalogoDesdeRespaldoLocal() {
        try {
            Path backupPath = Path.of("productos.xml");
            if (!Files.exists(backupPath)) {
                log.warn("No se encontró el archivo de respaldo local productos.xml.");
                return;
            }
            log.info("Cargando catálogo inicial de respaldo desde productos.xml...");
            String xmlResponse = Files.readString(backupPath, StandardCharsets.UTF_8);
            List<GestoPagoProducto> nuevosProductosSanos = parsearYFiltrarProductos(xmlResponse);
            procesarYGuardar(nuevosProductosSanos);
            log.info("Catálogo inicial cargado desde respaldo local con {} productos.", nuevosProductosSanos.size());
        } catch (Exception e) {
            log.error("Falló la carga inicial desde respaldo local ({})", e.getClass().getSimpleName());
        }
    }

    private List<GestoPagoProducto> parsearYFiltrarProductos(String xmlResponse) throws Exception {
        JAXBContext jaxbContext = JAXBContext.newInstance(GestoPagoProductResponse.class);
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        GestoPagoProductResponse response = (GestoPagoProductResponse) unmarshaller.unmarshal(new StringReader(xmlResponse));

        if (response == null || response.getProductos() == null || response.getProductos().isEmpty()) {
            throw new RuntimeException("El catálogo devuelto está vacío o hubo un error en la respuesta XML. No se sobrescribirá la base de datos.");
        }

        // 3.1 Validación de Integridad (Filtrar corruptos)
        return response.getProductos().stream()
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
    }

    private String obtenerXmlCatalogo() throws Exception {
        try {
            String bearerToken = tokenService.obtenerBearerToken(idDistribuidor, codigoDispositivo);
            log.info("Llamando a la API de GestoPago getProductList...");
            return productClient.getProductList(bearerToken);
        } catch (Exception apiEx) {
            log.warn("La llamada directa a getProductList de GestoPago no estuvo disponible ({}).", apiEx.getClass().getSimpleName());
            if (postgresRepository.count() < MIN_PRODUCTOS_CATALOGO_COMPLETO) {
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

        sincronizarPostgres(nuevosProductos, nuevosIds);
        sincronizarMongo(nuevosProductos, nuevosIds);
    }

    private void sincronizarPostgres(List<GestoPagoProducto> nuevosProductos, Set<Integer> nuevosIds) {
        // Fuente transaccional primaria: indexamos en memoria para guardar en un único lote (saveAll)
        List<ProductoEntity> allPostgres = postgresRepository.findAll();
        Map<Integer, ProductoEntity> pgPorId = new HashMap<>(allPostgres.size());
        Map<Integer, ProductoEntity> entidadesPorGuardar = new LinkedHashMap<>();

        for (ProductoEntity entity : allPostgres) {
            if (entity.getIdProducto() == null) {
                continue;
            }
            pgPorId.put(entity.getIdProducto(), entity);
            if (!nuevosIds.contains(entity.getIdProducto())) {
                entity.setActivo(false);
                entidadesPorGuardar.put(entity.getIdProducto(), entity);
            }
        }

        for (GestoPagoProducto dto : nuevosProductos) {
            ProductoEntity pgEntity = pgPorId.computeIfAbsent(dto.getIdProducto(), k -> new ProductoEntity());
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
            entidadesPorGuardar.put(dto.getIdProducto(), pgEntity);
        }

        if (!entidadesPorGuardar.isEmpty()) {
            postgresRepository.saveAll(new ArrayList<>(entidadesPorGuardar.values()));
        }
    }

    private void sincronizarMongo(List<GestoPagoProducto> nuevosProductos, Set<Integer> nuevosIds) {
        // Manejar MongoDB de forma aislada (si Mongo no está disponible, no interrumpe PostgreSQL)
        try {
            List<ProductoDocument> allMongo = mongoRepository.findAll();
            Map<Integer, ProductoDocument> mongoPorId = new HashMap<>(allMongo.size());
            Map<Integer, ProductoDocument> documentosPorGuardar = new LinkedHashMap<>();

            for (ProductoDocument doc : allMongo) {
                if (doc.getIdProducto() == null) {
                    continue;
                }
                mongoPorId.put(doc.getIdProducto(), doc);
                if (!nuevosIds.contains(doc.getIdProducto())) {
                    doc.setActivo(false);
                    documentosPorGuardar.put(doc.getIdProducto(), doc);
                }
            }

            for (GestoPagoProducto dto : nuevosProductos) {
                ProductoDocument mongoDoc = mongoPorId.computeIfAbsent(dto.getIdProducto(), k -> new ProductoDocument());
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
                documentosPorGuardar.put(dto.getIdProducto(), mongoDoc);
            }

            if (!documentosPorGuardar.isEmpty()) {
                mongoRepository.saveAll(new ArrayList<>(documentosPorGuardar.values()));
            }
        } catch (Exception mongoEx) {
            log.warn("Catálogo sincronizado en PostgreSQL; MongoDB no estuvo disponible en este ciclo ({}).",
                    mongoEx.getClass().getSimpleName());
        }
    }
}
