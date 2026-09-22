package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.gestopago.GestoPagoProductResponse;
import com.proyecto.servicios.service.GestoPagoProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/productos")
public class GestoPagoProductoController {

    private final GestoPagoProductService productService;

    public GestoPagoProductoController(GestoPagoProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public ResponseEntity<GestoPagoProductResponse> listarProductos() {
        // Retornamos el objeto DTO. ¡Spring Boot lo convierte a JSON automáticamente!
        return ResponseEntity.ok(productService.obtenerProductos());
    }
}
