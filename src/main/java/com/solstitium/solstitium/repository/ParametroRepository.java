package com.solstitium.solstitium.repository;

import com.solstitium.solstitium.model.entity.Parametro;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a los parámetros de referencia del simulador.
 * La carga del conjunto completo se delega al fragmento {@link CargaParametros}.
 * El guardado de cambios (guardarTodo) se agregará con la administración de parámetros.
 */
public interface ParametroRepository extends JpaRepository<Parametro, String>, CargaParametros {
}
