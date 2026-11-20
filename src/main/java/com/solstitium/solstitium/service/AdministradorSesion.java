package com.solstitium.solstitium.service;

import com.solstitium.solstitium.model.entity.Administrador;
import com.solstitium.solstitium.repository.AdministradorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** La identidad proviene del contexto autenticado por Spring Security, nunca de un DTO. */
@Component
@RequiredArgsConstructor
public class AdministradorSesion {
    private final AdministradorRepository administradores;

    public Administrador obtener() {
        var autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || !autenticacion.isAuthenticated()
                || autenticacion.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new AccessDeniedException("Se requiere una sesión de administrador.");
        }
        return administradores.buscarPorUsuario(autenticacion.getName()).filter(Administrador::isActivo)
                .orElseThrow(() -> new AccessDeniedException("La cuenta administrativa no está activa."));
    }
}
