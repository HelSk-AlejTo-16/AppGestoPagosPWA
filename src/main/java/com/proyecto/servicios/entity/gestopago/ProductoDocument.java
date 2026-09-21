package com.proyecto.servicios.entity.gestopago;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "gestopago_productos")
@Getter
@Setter
public class ProductoDocument {

    @Id
    private String id; // MongoDB usa String para sus ObjectIds nativos

    private Integer idProducto;
    private Integer idServicio;
    private String servicio;
    private String producto;
    private Double precio;
    private String legend;
    private Boolean activo = true;
}
