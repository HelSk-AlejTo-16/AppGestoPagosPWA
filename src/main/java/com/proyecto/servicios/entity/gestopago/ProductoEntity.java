package com.proyecto.servicios.entity.gestopago;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "gestopago_productos")
@Getter
@Setter
public class ProductoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "id_producto", nullable = false, unique = true)
    private Integer idProducto;

    @Column(name = "id_servicio")
    private Integer idServicio;

    @Column(name = "servicio")
    private String servicio;

    @Column(name = "producto_nombre")
    private String producto;

    @Column(name = "precio")
    private Double precio;

    @Column(name = "legend", columnDefinition = "TEXT")
    private String legend;

    @Column(name = "activo", nullable = false)
    private Boolean activo = true;
}
