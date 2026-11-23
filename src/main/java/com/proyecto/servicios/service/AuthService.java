package com.proyecto.servicios.service;

import com.proyecto.servicios.model.cliente.LoginRequest;
import com.proyecto.servicios.model.cliente.LoginResponse;
import com.proyecto.servicios.model.cliente.Reenviar2faRequest;
import com.proyecto.servicios.model.cliente.RestablecerPasswordRequest;
import com.proyecto.servicios.model.cliente.SolicitarRecuperacionRequest;
import com.proyecto.servicios.model.cliente.Verificar2faRequest;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    LoginResponse verificar2fa(Verificar2faRequest request);

    LoginResponse reenviar2fa(Reenviar2faRequest request);

    void solicitarRecuperacionPassword(SolicitarRecuperacionRequest request);

    void restablecerPassword(RestablecerPasswordRequest request);

    void logout(String correoAutenticado);
}
