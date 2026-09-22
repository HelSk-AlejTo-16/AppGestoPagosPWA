package com.proyecto.servicios.repositorys.gestopago;

import com.proyecto.servicios.entity.gestopago.ProductoDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductoMongoRepository extends MongoRepository<ProductoDocument, String> {
    Optional<ProductoDocument> findByIdProducto(Integer idProducto);
}
