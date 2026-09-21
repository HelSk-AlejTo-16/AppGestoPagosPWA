package com.proyecto.servicios.repositorys.gestopago;

import com.proyecto.servicios.entity.gestopago.ProductoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductoPostgresRepository extends JpaRepository<ProductoEntity, Integer> {
    Optional<ProductoEntity> findByIdProducto(Integer idProducto);
}
