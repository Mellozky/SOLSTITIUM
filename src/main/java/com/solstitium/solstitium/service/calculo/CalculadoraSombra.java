package com.solstitium.solstitium.service.calculo;

import com.solstitium.solstitium.exception.DatosSimulacionInvalidosException;
import com.solstitium.solstitium.model.dto.ConjuntoParametros;
import com.solstitium.solstitium.model.dto.FactorSombra;
import com.solstitium.solstitium.model.dto.RespuestaSombra;
import com.solstitium.solstitium.model.entity.FranjaHoraria;
import com.solstitium.solstitium.model.entity.PorcionAfectada;
import com.solstitium.solstitium.model.entity.TramoSombra;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** RF-03: pérdida acotada por franja, factor y tramo de sombreado. */
@Component
public class CalculadoraSombra {

    public FactorSombra calcularFactor(List<RespuestaSombra> respuestas, ConjuntoParametros conjunto) {
        if (conjunto == null || conjunto.getFranjas().isEmpty() || conjunto.getPorciones().isEmpty()) {
            throw new IllegalStateException("Deben cargarse las franjas y porciones de sombra.");
        }
        BigDecimal amplificacion = conjunto.getParametros().get("amplificacion_serie");
        if (amplificacion == null || amplificacion.signum() < 0) {
            throw new IllegalStateException("La amplificación en serie debe estar configurada y no ser negativa.");
        }
        Map<Long, FranjaHoraria> franjas = conjunto.getFranjas().stream()
                .collect(Collectors.toMap(FranjaHoraria::getId, Function.identity()));
        Map<Long, PorcionAfectada> porciones = conjunto.getPorciones().stream()
                .collect(Collectors.toMap(PorcionAfectada::getId, Function.identity()));
        BigDecimal sumaPesos = BigDecimal.ZERO;
        for (var franja : franjas.values()) {
            validarFraccion(franja.getPeso(), "peso de franja");
            sumaPesos = sumaPesos.add(franja.getPeso());
        }
        if (sumaPesos.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalStateException("Los pesos de las franjas deben sumar uno.");
        }
        if (respuestas == null || respuestas.size() != franjas.size()) {
            throw entradaInvalida("Debe responder una vez cada franja de sombra configurada.");
        }

        Set<Long> respondidas = new HashSet<>();
        BigDecimal perdida = BigDecimal.ZERO;
        for (var respuesta : respuestas) {
            if (respuesta == null || respuesta.getFranjaId() == null || respuesta.getPorcionId() == null) {
                throw entradaInvalida("Cada respuesta debe indicar una franja y una porción de sombra.");
            }
            var franja = franjas.get(respuesta.getFranjaId());
            var porcion = porciones.get(respuesta.getPorcionId());
            if (franja == null || porcion == null) {
                throw entradaInvalida("La franja o la porción de sombra seleccionada no existe.");
            }
            if (!respondidas.add(respuesta.getFranjaId())) {
                throw entradaInvalida("No puede responder más de una vez la misma franja de sombra.");
            }
            validarFraccion(porcion.getValor(), "porción de sombra");
            // Acotar ANTES de sumar: una franja nunca pierde más que su propio peso.
            BigDecimal perdidaFranja = porcion.getValor().multiply(amplificacion).min(BigDecimal.ONE);
            perdida = perdida.add(franja.getPeso().multiply(perdidaFranja));
        }
        BigDecimal factor = BigDecimal.ONE.subtract(perdida);
        TramoSombra tramo = clasificarTramo(factor, conjunto);
        if (tramo.getConfiguraciones().isEmpty()) {
            throw new IllegalStateException("El tramo de sombra no tiene configuraciones para evaluar.");
        }
        return new FactorSombra(perdida, factor, tramo);
    }

    /** Intervalos [mínimo, máximo), excepto un tramo puntual como sin sombra [1, 1]. */
    TramoSombra clasificarTramo(BigDecimal factor, ConjuntoParametros conjunto) {
        validarFraccion(factor, "factor de sombra");
        var coincidencias = conjunto.getTramos().stream().filter(tramo -> {
            BigDecimal minimo = tramo.getFactorMinimo();
            BigDecimal maximo = tramo.getFactorMaximo();
            validarFraccion(minimo, "mínimo de tramo");
            validarFraccion(maximo, "máximo de tramo");
            if (minimo.compareTo(maximo) > 0) {
                throw new IllegalStateException("Un tramo de sombra tiene sus extremos invertidos.");
            }
            return minimo.compareTo(maximo) == 0
                    ? factor.compareTo(minimo) == 0
                    : factor.compareTo(minimo) >= 0 && factor.compareTo(maximo) < 0;
        }).toList();
        if (coincidencias.size() != 1) {
            throw new IllegalStateException("El factor de sombra debe pertenecer exactamente a un tramo configurado.");
        }
        return coincidencias.getFirst();
    }

    private void validarFraccion(BigDecimal valor, String nombre) {
        if (valor == null || valor.signum() < 0 || valor.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException("El valor de " + nombre + " debe estar entre cero y uno.");
        }
    }

    private DatosSimulacionInvalidosException entradaInvalida(String mensaje) {
        return new DatosSimulacionInvalidosException(List.of(mensaje));
    }
}
