package com.proyecto.servicios.model.gestopago;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Data;

@Data
@XmlAccessorType(XmlAccessType.FIELD)
public class GestoPagoProducto {
    
    // Los datos del producto vienen como "atributos" dentro de la etiqueta <producto>
    @XmlAttribute
    private String servicio;
    
    @XmlAttribute
    private String producto;
    
    @XmlAttribute
    private Integer idServicio;
    
    @XmlAttribute
    private Integer idProducto;
    
    @XmlAttribute
    private Integer idCatTipoServicio;
    
    @XmlAttribute
    private Integer tipoFront;
    
    @XmlAttribute
    private Boolean hasDigitoVerificador;
    
    @XmlAttribute
    private Double precio;
    
    @XmlAttribute
    private Boolean showAyuda;
    
    @XmlAttribute
    private String tipoReferencia;
    
    // El legend viene como un nodo interno, por lo que usamos XmlElement
    @XmlElement(name = "legend")
    private String legend;
}
