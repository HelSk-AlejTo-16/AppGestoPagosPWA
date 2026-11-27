package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoProductResponse;
import com.proyecto.servicios.model.gestopago.ProductoBusquedaRequest;
import com.proyecto.servicios.service.GestoPagoProductService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

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

    @GetMapping(value = "/getServiceLogo")
    public ResponseEntity<byte[]> obtenerLogoServicio(
            @RequestParam(required = false) Integer idServicio,
            @RequestParam(required = false) String servicio) {
        byte[] logo = productService.getServiceLogo(idServicio, servicio);
        MediaType contentType = detectarMediaType(logo);
        return ResponseEntity.ok()
                .contentType(contentType)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .body(logo);
    }

    @GetMapping(value = "/servicios/{idServicio}/logo")
    public ResponseEntity<byte[]> obtenerLogoPorRuta(
            @PathVariable Integer idServicio,
            @RequestParam(required = false) String servicio) {
        return obtenerLogoServicio(idServicio, servicio);
    }

    private static MediaType detectarMediaType(byte[] data) {
        if (data != null && data.length >= 4) {
            if ((data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
                return MediaType.IMAGE_PNG;
            }
            if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
                return MediaType.IMAGE_JPEG;
            }
        }
        return MediaType.valueOf("image/svg+xml");
    }
}
