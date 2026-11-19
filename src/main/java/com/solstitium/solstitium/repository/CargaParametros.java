package com.solstitium.solstitium.repository;

import com.solstitium.solstitium.model.dto.ConjuntoParametros;

/** Consulta compuesta que amplía el repositorio de parámetros de Spring Data. */
public interface CargaParametros {

    /** Carga la configuración disponible; falla si falta algún grupo o la referencia activa. */
    ConjuntoParametros obtenerParametros();
}
