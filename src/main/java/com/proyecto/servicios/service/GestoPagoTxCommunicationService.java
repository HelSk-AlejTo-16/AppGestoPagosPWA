package com.proyecto.servicios.service;

import com.proyecto.servicios.model.gestopago.tx.GestoPagoTxResponse;

import java.math.BigDecimal;

public interface GestoPagoTxCommunicationService {

    /**
     * Ejecuta la llamada síncrona contra el endpoint sendTx.do de GestoPago.
     *
     * @param idServicio Identificador del servicio en GestoPago
     * @param idProducto Identificador del producto en GestoPago
     * @param referencia Teléfono o número de servicio
     * @param monto      Monto a liquidar
     * @param upc        Idempotency Key de 10 caracteres
     * @return Objeto de respuesta parseado con código, folio y mensaje
     */
    GestoPagoTxResponse ejecutarTransaccion(
            String idServicio,
            String idProducto,
            String referencia,
            BigDecimal monto,
            String upc
    );
}

