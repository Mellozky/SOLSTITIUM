package com.solstitium.solstitium.repository;

import com.solstitium.solstitium.model.dto.ConjuntoParametros;
import com.solstitium.solstitium.model.entity.Configuracion;
import com.solstitium.solstitium.model.entity.Estrato;
import com.solstitium.solstitium.model.entity.FranjaHoraria;
import com.solstitium.solstitium.model.entity.LimiteParametro;
import com.solstitium.solstitium.model.entity.Panel;
import com.solstitium.solstitium.model.entity.Parametro;
import com.solstitium.solstitium.model.entity.PorcionAfectada;
import com.solstitium.solstitium.model.entity.RangoViabilidad;
import com.solstitium.solstitium.model.entity.TramoSombra;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Implementación del fragmento CargaParametros, descubierto por Spring Data. */
@RequiredArgsConstructor
public class CargaParametrosImpl implements CargaParametros {

    private final EntityManager gestorEntidades;

    /**
     * Lectura repetible: todas las consultas observan la misma versión de PostgreSQL
     * cuando este método inicia la transacción. Si participa en otra, hereda su aislamiento.
     * No incorpora valores por defecto ni sustituye al futuro ValidadorParametros.
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ConjuntoParametros obtenerParametros() {
        Map<String, BigDecimal> parametros = new LinkedHashMap<>();
        consultar("select p from Parametro p order by p.clave", Parametro.class, "parámetros generales")
                .forEach(parametro -> parametros.put(parametro.getClave(), parametro.getValor()));

        List<Panel> referencias = gestorEntidades.createQuery(
                "select p from Panel p where p.esReferencia = true and p.activo = true", Panel.class)
                .getResultList();
        if (referencias.size() != 1) {
            throw new IllegalStateException("Debe existir exactamente un panel de referencia activo.");
        }

        return new ConjuntoParametros(
                Map.copyOf(parametros),
                consultar("select e from Estrato e order by e.numero", Estrato.class, "estratos"),
                consultar("select f from FranjaHoraria f order by f.orden, f.id", FranjaHoraria.class, "franjas horarias"),
                consultar("select p from PorcionAfectada p order by p.orden, p.id", PorcionAfectada.class, "porciones afectadas"),
                // LEFT JOIN conserva también los tramos sin configuraciones para su posterior validación.
                consultar("select distinct t from TramoSombra t left join fetch t.configuraciones order by t.orden, t.id",
                        TramoSombra.class, "tramos de sombra"),
                consultar("select c from Configuracion c order by c.id", Configuracion.class, "configuraciones"),
                consultar("select r from RangoViabilidad r order by r.orden, r.id", RangoViabilidad.class, "rangos de viabilidad"),
                referencias.getFirst(),
                consultar("select l from LimiteParametro l order by l.tabla, l.campo, l.clave, l.id",
                        LimiteParametro.class, "límites de parámetros"));
    }

    private <T> List<T> consultar(String consulta, Class<T> tipo, String grupo) {
        List<T> resultados = gestorEntidades.createQuery(consulta, tipo).getResultList();
        if (resultados.isEmpty()) {
            throw new IllegalStateException("Faltan datos de configuración: " + grupo + ".");
        }
        return List.copyOf(resultados);
    }
}
