package com.solstitium.solstitium.repository;

import com.solstitium.solstitium.model.entity.Administrador;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a los administradores. Ningún caso de uso los crea (ERS): los casos de uso solo los
 * consultan y el alta se hace por script al instalar, mediante AprovisionamientoAdministrador.
 */
public interface AdministradorRepository extends Repository<Administrador, Long> {

    /** Busca un administrador por su nombre de usuario (activo o no). */
    @Query("select a from Administrador a where a.usuario = :usuario")
    Optional<Administrador> buscarPorUsuario(@Param("usuario") String usuario);

    /** Solo para el aprovisionamiento por script: registra o actualiza un administrador. */
    Administrador save(Administrador administrador);
}
