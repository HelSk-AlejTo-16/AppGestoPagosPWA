package com.proyecto.servicios.model.gestopago;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Data;
import java.util.List;

@Data
// Indicamos que esta clase es la raíz principal del XML
@XmlRootElement(name = "RESPONSE")
@XmlAccessorType(XmlAccessType.FIELD)
public class GestoPagoProductResponse {
    
    @XmlElement(name = "MENSAJE")
    private GestoPagoMensaje mensaje;
    
    // XmlElementWrapper lee el nodo <PRODUCTOS> que envuelve la lista
    // XmlElement le dice que internamente hay muchos nodos <producto>
    @XmlElementWrapper(name = "PRODUCTOS")
    @XmlElement(name = "producto")
    private List<GestoPagoProducto> productos;
}
