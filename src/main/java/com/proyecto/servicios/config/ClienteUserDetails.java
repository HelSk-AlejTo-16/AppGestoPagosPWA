package com.proyecto.servicios.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public class ClienteUserDetails implements UserDetails {

    private final String username;
    private final String password;
    private final String idSesionActiva;
    private final String plataformaActiva;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean enabled;

    public ClienteUserDetails(String username, String password, String idSesionActiva,
                              String plataformaActiva, Collection<? extends GrantedAuthority> authorities,
                              boolean enabled) {
        this.username = username;
        this.password = password;
        this.idSesionActiva = idSesionActiva;
        this.plataformaActiva = plataformaActiva;
        this.authorities = authorities;
        this.enabled = enabled;
    }

    public String getIdSesionActiva() {
        return idSesionActiva;
    }

    public String getPlataformaActiva() {
        return plataformaActiva;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
