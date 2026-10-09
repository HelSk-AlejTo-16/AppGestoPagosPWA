package com.proyecto.servicios.config;

import com.proyecto.servicios.entity.cliente.Usuario;
import com.proyecto.servicios.repositorys.cliente.UsuarioRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClienteUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public ClienteUserDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByCorreoIgnoreCase(correo)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado."));
        boolean activo = usuario.isActivo() && usuario.getCliente().isActivo();
        return User.withUsername(usuario.getCorreo())
                .password(usuario.getPassword())
                .authorities(List.of(new SimpleGrantedAuthority(activo ? "ROLE_USER" : "ROLE_RECOVERY")))
                .build();
    }
}
