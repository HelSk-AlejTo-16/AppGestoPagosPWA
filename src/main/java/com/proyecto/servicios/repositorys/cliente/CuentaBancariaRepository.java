package com.proyecto.servicios.repositorys.cliente;

import com.proyecto.servicios.entity.cliente.CuentaBancaria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CuentaBancariaRepository extends JpaRepository<CuentaBancaria, UUID>,
        JpaSpecificationExecutor<CuentaBancaria> {

    boolean existsByNumeroCuenta(String numeroCuenta);

    boolean existsByClabe(String clabe);

    Optional<CuentaBancaria> findByNumeroCuenta(String numeroCuenta);

    Page<CuentaBancaria> findAllByOrderByCreadoEnDesc(Pageable pageable);

    Page<CuentaBancaria> findAllByActivaOrderByCreadoEnDesc(boolean activa, Pageable pageable);

    Page<CuentaBancaria> findAllByActivaTrueOrderByCreadoEnDesc(Pageable pageable);

    List<CuentaBancaria> findAllByCliente_IdPersonaIn(Collection<UUID> clienteIds);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CuentaBancaria c SET c.saldo = c.saldo - :monto WHERE c.idBancaria = :idBancaria AND c.saldo >= :monto AND c.activa = true")
    int debitarSaldo(@Param("idBancaria") UUID idBancaria, @Param("monto") BigDecimal monto);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CuentaBancaria c SET c.saldo = c.saldo + :monto WHERE c.idBancaria = :idBancaria")
    int abonarSaldo(@Param("idBancaria") UUID idBancaria, @Param("monto") BigDecimal monto);
}
