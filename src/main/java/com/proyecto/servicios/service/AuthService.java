package com.proyecto.servicios.service;

import com.proyecto.servicios.model.cliente.LoginRequest;
import com.proyecto.servicios.model.cliente.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);
}
