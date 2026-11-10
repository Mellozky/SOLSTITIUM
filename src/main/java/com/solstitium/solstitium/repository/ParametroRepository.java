package com.solstitium.solstitium.repository;

import com.solstitium.solstitium.model.entity.Parametro;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a los parámetros de referencia del simulador.
 * La carga de todos los parámetros en un solo objeto (obtenerParametros) y el guardado
 * de cambios (guardarTodo) se agregan con las funcionalidades que los usan.
 */
public interface ParametroRepository extends JpaRepository<Parametro, String> {
}
