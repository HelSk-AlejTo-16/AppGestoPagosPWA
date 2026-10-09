package com.proyecto.servicios.repositorys.cliente;

import com.proyecto.servicios.entity.cliente.CuentaBancaria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CuentaBancariaRepository extends JpaRepository<CuentaBancaria, UUID> {

    boolean existsByNumeroCuenta(String numeroCuenta);

    boolean existsByClabe(String clabe);

    Optional<CuentaBancaria> findByNumeroCuenta(String numeroCuenta);

    Page<CuentaBancaria> findAllByActivaTrueOrderByCreadoEnDesc(Pageable pageable);

    List<CuentaBancaria> findAllByCliente_IdPersonaIn(Collection<UUID> clienteIds);
}
