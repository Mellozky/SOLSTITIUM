package com.solstitium.solstitium.repository;

import com.solstitium.solstitium.model.entity.Administrador;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Acceso a los administradores. Solo lectura: se cargan por script y ningún caso de uso los crea.
 */
public interface AdministradorRepository extends Repository<Administrador, Long> {

    /** Busca un administrador por su nombre de usuario (activo o no). */
    @Query("select a from Administrador a where a.usuario = :usuario")
    Optional<Administrador> buscarPorUsuario(@Param("usuario") String usuario);
}
