package com.solstitium.solstitium.model.dto;

import com.solstitium.solstitium.model.entity.Configuracion;
import com.solstitium.solstitium.model.entity.Estrato;
import com.solstitium.solstitium.model.entity.FranjaHoraria;
import com.solstitium.solstitium.model.entity.LimiteParametro;
import com.solstitium.solstitium.model.entity.Panel;
import com.solstitium.solstitium.model.entity.PorcionAfectada;
import com.solstitium.solstitium.model.entity.RangoViabilidad;
import com.solstitium.solstitium.model.entity.TramoSombra;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Parámetros reunidos para iniciar una simulación o validar cambios (ERS, figura 5).
 * Es un objeto interno: no se persiste ni se expone directamente por la API.
 */
@Getter
@RequiredArgsConstructor
public class ConjuntoParametros {

    private final Map<String, BigDecimal> parametros;
    private final List<Estrato> estratos;
    private final List<FranjaHoraria> franjas;
    private final List<PorcionAfectada> porciones;
    private final List<TramoSombra> tramos;
    private final List<Configuracion> configuraciones;
    private final List<RangoViabilidad> rangos;
    private final Panel panelReferencia;
    private final List<LimiteParametro> limites;
}
