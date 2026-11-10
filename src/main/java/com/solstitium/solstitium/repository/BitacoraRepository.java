package com.solstitium.solstitium.repository;

import com.solstitium.solstitium.model.entity.CambioBitacora;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Acceso a la bitácora de cambios de parámetros. */
public interface BitacoraRepository extends JpaRepository<CambioBitacora, Long> {

    /**
     * Cambios registrados, el más reciente primero. Trae el administrador en la misma
     * consulta porque la relación es perezosa y la bitácora siempre lo muestra.
     */
    @Query("select c from CambioBitacora c join fetch c.administrador order by c.fecha desc, c.id desc")
    List<CambioBitacora> listarRecientes();
}
