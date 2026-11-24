package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoProductResponse;
import com.proyecto.servicios.model.gestopago.ProductoBusquedaRequest;
import com.proyecto.servicios.service.GestoPagoProductService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
        return ResponseEntity.ok(productService.obtenerProductos());
    }

    @PostMapping("/buscar")
    public ResponseEntity<GenericResponse> buscarProductosPaginados(
            @Valid @RequestBody(required = false) ProductoBusquedaRequest request) {
        return ResponseEntity.ok(GenericResponse.ok(
                "Consulta paginada de productos exitosa.",
                productService.buscarProductosPaginados(request)));
    }

    @PostMapping("/servicios/buscar")
    public ResponseEntity<GenericResponse> buscarServiciosPaginados(
            @Valid @RequestBody(required = false) ProductoBusquedaRequest request) {
        return ResponseEntity.ok(GenericResponse.ok(
                "Consulta paginada de servicios exitosa.",
                productService.buscarServiciosPaginados(request)));
    }
}
