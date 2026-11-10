package com.solstitium.solstitium.repository;

import com.solstitium.solstitium.model.entity.Panel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Acceso al catálogo de paneles. Los paneles nunca se eliminan: se activan o desactivan. */
public interface PanelRepository extends JpaRepository<Panel, Long> {

    /** Todos los paneles del catálogo, activos e inactivos, ordenados por marca y modelo. */
    @Query("select p from Panel p order by p.marca, p.modelo")
    List<Panel> buscarTodos();

    /** El panel de referencia. La base garantiza que hay como máximo uno. */
    @Query("select p from Panel p where p.esReferencia = true")
    Optional<Panel> buscarReferencia();

    /** Registra un panel nuevo o guarda los cambios de uno existente. */
    default Panel guardar(Panel panel) {
        return save(panel);
    }
}
