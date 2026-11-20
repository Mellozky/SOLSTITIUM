package com.solstitium.solstitium.repository;

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
import jakarta.persistence.LockModeType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** Consultas fijas: ningún nombre procedente de la petición se interpreta como JPQL o SQL. */
@Repository
@RequiredArgsConstructor
public class AdministracionParametrosRepository {
    private final EntityManager gestor;

    public Datos cargar(boolean bloquear) {
        // Todos los lotes administrativos bloquean las mismas filas antes de leer el conjunto.
        var consulta = gestor.createQuery("select p from Parametro p order by p.clave", Parametro.class);
        if (bloquear) consulta.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        var parametros = consulta.getResultList();
        return new Datos(parametros,
                gestor.createQuery("select e from Estrato e order by e.numero", Estrato.class).getResultList(),
                gestor.createQuery("select f from FranjaHoraria f order by f.orden, f.id", FranjaHoraria.class).getResultList(),
                gestor.createQuery("select p from PorcionAfectada p order by p.orden, p.id", PorcionAfectada.class).getResultList(),
                gestor.createQuery("select distinct t from TramoSombra t left join fetch t.configuraciones order by t.orden, t.id", TramoSombra.class).getResultList(),
                gestor.createQuery("select c from Configuracion c order by c.id", Configuracion.class).getResultList(),
                gestor.createQuery("select r from RangoViabilidad r order by r.orden, r.id", RangoViabilidad.class).getResultList(),
                gestor.createQuery("select p from Panel p order by p.id", Panel.class).getResultList(),
                gestor.createQuery("select l from LimiteParametro l order by l.tabla, l.campo, l.clave", LimiteParametro.class).getResultList());
    }

    public void sincronizar() { gestor.flush(); }

    public record Datos(List<Parametro> parametros, List<Estrato> estratos, List<FranjaHoraria> franjas,
                        List<PorcionAfectada> porciones, List<TramoSombra> tramos,
                        List<Configuracion> configuraciones, List<RangoViabilidad> rangos,
                        List<Panel> paneles, List<LimiteParametro> limites) {}
}
