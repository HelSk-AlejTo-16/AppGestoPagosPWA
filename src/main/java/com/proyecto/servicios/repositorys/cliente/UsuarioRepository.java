package com.proyecto.servicios.repositorys.cliente;

import com.proyecto.servicios.entity.cliente.Usuario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    boolean existsByCorreoIgnoreCase(String correo);

    @EntityGraph(attributePaths = "cliente")
    Optional<Usuario> findByCorreoIgnoreCase(String correo);

    @EntityGraph(attributePaths = "cliente")
    Optional<Usuario> findByIdUsuario(UUID idUsuario);
}
