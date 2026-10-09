package com.proyecto.servicios.repositorys.cliente;

import com.proyecto.servicios.entity.cliente.Cliente;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClienteRepository extends JpaRepository<Cliente, UUID> {

    boolean existsByCurpIgnoreCase(String curp);

    boolean existsByRfcIgnoreCase(String rfc);

    @EntityGraph(attributePaths = {"contacto", "direccion", "informacionLaboral", "usuario", "cuentas"})
    Optional<Cliente> findWithRelationsByIdPersona(UUID idPersona);

    @EntityGraph(attributePaths = {"contacto", "direccion", "informacionLaboral", "usuario", "cuentas"})
    Optional<Cliente> findWithRelationsByCurpIgnoreCase(String curp);

    @EntityGraph(attributePaths = {"contacto", "direccion", "informacionLaboral", "usuario", "cuentas"})
    Optional<Cliente> findWithRelationsByRfcIgnoreCase(String rfc);

    @EntityGraph(attributePaths = {"contacto", "direccion", "informacionLaboral", "usuario", "cuentas"})
    Optional<Cliente> findWithRelationsByUsuarioCorreoIgnoreCase(String correo);

    @EntityGraph(attributePaths = {"contacto", "direccion", "informacionLaboral", "usuario"})
    Page<Cliente> findAllByOrderByCreadoEnDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"contacto", "direccion", "informacionLaboral", "usuario"})
    Page<Cliente> findAllByActivoTrueOrderByCreadoEnDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"contacto", "direccion", "informacionLaboral", "usuario"})
    Page<Cliente> findAllByCreadoEnGreaterThanEqualAndCreadoEnLessThanOrderByCreadoEnDesc(
            OffsetDateTime desde, OffsetDateTime hasta, Pageable pageable
    );
}
