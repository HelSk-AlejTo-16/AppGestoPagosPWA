package com.proyecto.servicios.client;

import feign.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "gestoPagoProduct", url = "${gestopago.auth.url}")
public interface GestoPagoProductClient {

    @GetMapping("/sistema/service/getProductList.do")
    String getProductList(@RequestHeader("Authorization") String token);

    @GetMapping("/sistema/service/getServiceLogo.do")
    Response getServiceLogo(
            @RequestHeader("Authorization") String token,
            @RequestParam("idServicio") Integer idServicio
    );
}
