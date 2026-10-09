package com.proyecto.servicios.service;

import com.proyecto.servicios.model.cliente.ClienteActualizarRequest;
import com.proyecto.servicios.model.cliente.ClienteRegistroRequest;
import com.proyecto.servicios.model.cliente.ClienteResponse;
import com.proyecto.servicios.model.cliente.PaginaResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface ClienteService {

    ClienteResponse registrar(ClienteRegistroRequest request);

    PaginaResponse<ClienteResponse> obtenerTodos(int pagina, int tamano);

    PaginaResponse<ClienteResponse> obtenerActivos(int pagina, int tamano);

    ClienteResponse obtenerPorId(UUID id);

    ClienteResponse obtenerPorIdPropio(UUID id, String correoAutenticado);

    ClienteResponse obtenerPorCurp(String curp);

    ClienteResponse obtenerPorRfc(String rfc);

    ClienteResponse obtenerPorCorreo(String correo);

    ClienteResponse obtenerPorNumeroCuenta(String numeroCuenta);

    PaginaResponse<ClienteResponse> obtenerRegistrados(LocalDate desde, LocalDate hasta, int pagina, int tamano);

    ClienteResponse actualizar(UUID id, String correoAutenticado, ClienteActualizarRequest request);

    void desactivar(UUID id, String correoAutenticado);

    ClienteResponse reactivar(UUID id);
}
