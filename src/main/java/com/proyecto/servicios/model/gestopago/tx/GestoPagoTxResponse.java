package com.proyecto.servicios.model.gestopago.tx;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Data;

@Data
@XmlRootElement(name = "RESPONSE")
@XmlAccessorType(XmlAccessType.FIELD)
public class GestoPagoTxResponse {

    @XmlElement(name = "CODIGO")
    private String codigo;

    @XmlElement(name = "FOLIO")
    private String folio;

    @XmlElement(name = "AUTORIZACION")
    private String autorizacion;

    @XmlElement(name = "TEXTO")
    private String texto;

    @XmlElement(name = "FECHA")
    private String fecha;

    @XmlElement(name = "HORA")
    private String hora;

    /**
     * Retorna verdadero si la transacción fue aprobada con éxito por GestoPago (Código 01 o 00).
     */
    public boolean isAprobada() {
        return "01".equals(codigo) || "1".equals(codigo) || "00".equals(codigo);
    }
}

