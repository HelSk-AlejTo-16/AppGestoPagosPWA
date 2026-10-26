package com.proyecto.servicios.repositorys.pagos;

import com.proyecto.servicios.entity.pagos.EstadoPagoServicio;
import com.proyecto.servicios.entity.pagos.PagoServicioEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PagoServicioRepository extends JpaRepository<PagoServicioEntity, UUID>,
        JpaSpecificationExecutor<PagoServicioEntity> {

    Optional<PagoServicioEntity> findByUpc(String upc);

    boolean existsByUpc(String upc);

    /**
     * Detecta transacciones duplicadas para la misma cuenta, producto, referencia y monto
     * dentro de una ventana de tiempo reciente.
     */
    boolean existsByCuenta_IdBancariaAndIdProductoAndReferenciaAndMontoAndEstadoInAndCreadoEnAfter(
            UUID cuentaId,
            String idProducto,
            String referencia,
            BigDecimal monto,
            Collection<EstadoPagoServicio> estados,
            OffsetDateTime creadoEn
    );

    Page<PagoServicioEntity> findAllByCuenta_IdBancariaOrderByCreadoEnDesc(UUID cuentaId, Pageable pageable);

    Page<PagoServicioEntity> findAllByEstadoOrderByCreadoEnDesc(EstadoPagoServicio estado, Pageable pageable);
}
