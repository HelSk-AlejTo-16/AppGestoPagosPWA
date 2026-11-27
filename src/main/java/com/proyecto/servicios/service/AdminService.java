package com.proyecto.servicios.service;

import com.proyecto.servicios.model.admin.AdminAbonoCuentaRequest;
import com.proyecto.servicios.model.admin.AdminResumenResponse;
import com.proyecto.servicios.model.admin.AdminUsuarioBusquedaRequest;
import com.proyecto.servicios.model.admin.AdminUsuarioItemResponse;
import com.proyecto.servicios.model.cliente.CuentaSaldoResponse;
import com.proyecto.servicios.model.cliente.PaginaResponse;

import java.util.UUID;

public interface AdminService {

    AdminResumenResponse obtenerResumen();

    PaginaResponse<AdminUsuarioItemResponse> buscarUsuarios(AdminUsuarioBusquedaRequest criterios);

    AdminUsuarioItemResponse cambiarEstadoUsuario(UUID idCliente, boolean activo, String correoAdmin);

    AdminUsuarioItemResponse cambiarRolUsuario(UUID idCliente, String nuevoRol, String correoAdmin);

    AdminUsuarioItemResponse desbloquearUsuario(UUID idCliente);

    AdminUsuarioItemResponse forzarCierreSesion(UUID idCliente);

    CuentaSaldoResponse abonarSaldoACuenta(AdminAbonoCuentaRequest request, String correoAdmin);
}
