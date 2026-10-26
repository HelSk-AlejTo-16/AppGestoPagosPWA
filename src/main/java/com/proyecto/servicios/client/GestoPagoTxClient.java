package com.proyecto.servicios.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "gestoPagoTx", url = "${gestopago.auth.url}")
public interface GestoPagoTxClient {

    @PostMapping("/sistema/service/sendTx.do")
    String sendTransaction(
            @RequestHeader("Authorization") String token,
            @RequestParam("idServicio") String idServicio,
            @RequestParam("idProducto") String idProducto,
            @RequestParam("referencia") String referencia,
            @RequestParam("montoPago") String montoPago,
            @RequestParam("horaLocal") String horaLocal,
            @RequestParam("upc") String upc
    );
}

