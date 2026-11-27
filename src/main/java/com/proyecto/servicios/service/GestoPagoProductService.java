package com.proyecto.servicios.service;

import com.proyecto.servicios.model.cliente.PaginaResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoProductResponse;
import com.proyecto.servicios.model.gestopago.GestoPagoProducto;
import com.proyecto.servicios.model.gestopago.ProductoBusquedaRequest;
import com.proyecto.servicios.model.gestopago.ServicioResumenDto;

public interface GestoPagoProductService {

    GestoPagoProductResponse obtenerProductos();

    PaginaResponse<GestoPagoProducto> buscarProductosPaginados(ProductoBusquedaRequest request);

    PaginaResponse<ServicioResumenDto> buscarServiciosPaginados(ProductoBusquedaRequest request);

    byte[] getServiceLogo(Integer idServicio, String nombreServicio);
}
