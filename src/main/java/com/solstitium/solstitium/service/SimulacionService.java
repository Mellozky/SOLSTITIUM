package com.solstitium.solstitium.service;

import com.solstitium.solstitium.model.dto.ConjuntoParametros;
import com.solstitium.solstitium.model.dto.FactorSombra;
import com.solstitium.solstitium.model.dto.ResultadoConfiguracion;
import com.solstitium.solstitium.model.dto.ResultadoSimulacion;
import com.solstitium.solstitium.model.dto.SolicitudSimulacion;
import com.solstitium.solstitium.repository.ParametroRepository;
import com.solstitium.solstitium.service.calculo.CalculadoraFinanciera;
import com.solstitium.solstitium.service.calculo.CalculadoraSombra;
import com.solstitium.solstitium.service.calculo.CalculadoraTarifa;
import com.solstitium.solstitium.service.calculo.CalculadoraTecnica;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Coordinador del ERS: valida, calcula y recomienda sin almacenar simulaciones. */
@Service
@RequiredArgsConstructor
public class SimulacionService {
    private final ParametroRepository repositorio;
    private final ValidadorSolicitudSimulacion validador;
    private final CalculadoraSombra calculadoraSombra;
    private final CalculadoraTecnica calculadoraTecnica;
    private final CalculadoraFinanciera calculadoraFinanciera;
    private final CalculadoraTarifa calculadoraTarifa;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ResultadoSimulacion simular(SolicitudSimulacion solicitud) {
        ConjuntoParametros parametros = repositorio.obtenerParametros();
        List<String> advertencias = new ArrayList<>(validador.validar(solicitud, parametros));
        advertencias.add("Esta simulación es una estimación, no una cotización.");
        FactorSombra sombra = calculadoraSombra.calcularFactor(solicitud.getRespuestasSombra(), parametros);
        if (sombra.getTramo().isMuestraAdvertencia()) {
            advertencias.add("Baja confianza por sombreado: se recomienda una evaluación en sitio.");
        }
        ResultadoSimulacion respuesta = new ResultadoSimulacion();
        respuesta.setTramo(sombra.getTramo().getNombre());
        respuesta.setFactorSombra(sombra.getFactor());
        respuesta.setAdvertencias(List.copyOf(advertencias));
        respuesta.setPanelReferencia(parametros.getPanelReferencia().getMarca() + " " + parametros.getPanelReferencia().getModelo());
        respuesta.setFechaTarifa(repositorio.findById("costo_unitario_kwh").orElseThrow(
                () -> new IllegalStateException("Falta el costo unitario de la tarifa.")).getFechaActualizacion());
        respuesta.setGastoMensualSinSistema(calculadoraTarifa.calcularCostoMensual(
                solicitud.consumoPromedio(), solicitud.getEstrato(), parametros));

        List<ResultadoConfiguracion> resultados = new ArrayList<>();
        for (var configuracion : sombra.getTramo().getConfiguraciones()) {
            var resultado = calculadoraTecnica.dimensionar(solicitud, sombra, configuracion, parametros);
            if (resultado.getPaneles() == 0) {
                respuesta.setSinEspacio(true);
                respuesta.setMotivoRecomendacion("El techo no dispone de espacio suficiente para instalar un panel.");
                return respuesta;
            }
            calculadoraFinanciera.calcularIndicadores(resultado, solicitud, parametros, sombra);
            resultados.add(resultado);
        }
        respuesta.setResultados(List.copyOf(resultados));
        var recomendacion = elegirRecomendacion(resultados);
        respuesta.setRecomendada(recomendacion == null ? null : recomendacion.getConfiguracion());
        if (recomendacion == null) {
            respuesta.setMotivoRecomendacion("Ninguna configuración recupera la inversión durante la vida útil.");
        } else if (resultados.size() == 1) {
            respuesta.setMotivoRecomendacion("Es la única configuración evaluada y recupera la inversión.");
        } else {
            boolean empate = resultados.stream().filter(resultado -> resultado.getTiempoRecuperacion() != null)
                    .filter(resultado -> resultado.getTiempoRecuperacion().compareTo(recomendacion.getTiempoRecuperacion()) == 0)
                    .count() > 1;
            respuesta.setMotivoRecomendacion(empate
                    ? "Las configuraciones recuperan la inversión en el mismo tiempo; se presenta " + recomendacion.getConfiguracion() + "."
                    : "Se recomienda " + recomendacion.getConfiguracion() + " por su menor tiempo de recuperación.");
        }
        if (sombra.getFactor().compareTo(BigDecimal.ONE) < 0) {
            var central = parametros.getConfiguraciones().stream().filter(actual -> actual.getCodigo().equals("central"))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Falta la configuración central."));
            var sinSombra = new FactorSombra(BigDecimal.ZERO, BigDecimal.ONE,
                    calculadoraSombra.clasificarTramo(BigDecimal.ONE, parametros));
            var escenario = calculadoraTecnica.dimensionar(solicitud, sinSombra, central, parametros);
            calculadoraFinanciera.calcularIndicadores(escenario, solicitud, parametros, sinSombra);
            respuesta.setEscenarioSinSombra(escenario);
        }
        return respuesta;
    }

    /** Un resultado que recupera puede recomendarse aunque el rango lo clasifique como no viable. */
    public ResultadoConfiguracion elegirRecomendacion(List<ResultadoConfiguracion> resultados) {
        return resultados.stream().filter(resultado -> resultado.getTiempoRecuperacion() != null)
                .min(Comparator.comparing(ResultadoConfiguracion::getTiempoRecuperacion)).orElse(null);
    }
}
