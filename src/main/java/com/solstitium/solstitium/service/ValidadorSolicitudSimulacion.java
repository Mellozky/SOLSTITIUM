package com.solstitium.solstitium.service;

import com.solstitium.solstitium.exception.DatosSimulacionInvalidosException;
import com.solstitium.solstitium.model.dto.ConjuntoParametros;
import com.solstitium.solstitium.model.dto.ParametrosEntradaSimulacion;
import com.solstitium.solstitium.model.dto.ParametrosEntradaSimulacion.EstratoDisponible;
import com.solstitium.solstitium.model.dto.SolicitudSimulacion;
import com.solstitium.solstitium.service.calculo.CalculadoraTarifa;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Valida RF-01/RF-02 sin consultar repetidamente la BD ni modificar sus parámetros. */
@Component
@RequiredArgsConstructor
public class ValidadorSolicitudSimulacion {

    private final Validator validador;
    private final CalculadoraTarifa calculadoraTarifa;

    /**
     * El futuro SimulacionService cargará el conjunto una vez y lo compartirá con las calculadoras.
     * Devuelve advertencias no bloqueantes; los errores impiden continuar.
     * No sustituye al ValidadorParametros de edición administrativa del ERS.
     */
    public List<String> validar(SolicitudSimulacion solicitud, ConjuntoParametros conjunto) {
        if (solicitud == null) {
            throw new DatosSimulacionInvalidosException(List.of("Debe enviar los datos de la simulación."));
        }
        List<String> erroresEstructura = validador.validate(solicitud).stream()
                .map(error -> error.getPropertyPath() + ": " + error.getMessage()).sorted().toList();
        rechazarSiHayErrores(erroresEstructura);
        if (conjunto == null) {
            throw new IllegalStateException("No se cargaron los parámetros de la simulación.");
        }

        List<String> errores = new ArrayList<>();
        List<String> advertencias = new ArrayList<>();
        ParametrosEntradaSimulacion parametros = consultarParametrosEntrada(conjunto);
        revisarConsumos(solicitud.getConsumos(), parametros, errores, advertencias);
        validarArea(solicitud.getAreaLibre(), parametros, errores);
        if (parametros.estratos().stream().noneMatch(estrato -> estrato.numero() == solicitud.getEstrato())) {
            errores.add("El estrato seleccionado no está disponible en la configuración.");
        }
        validarSombra(solicitud, conjunto, errores);
        rechazarSiHayErrores(errores);
        calculadoraTarifa.obtenerAdvertenciaFactura(solicitud, conjunto).ifPresent(advertencias::add);
        return List.copyOf(advertencias);
    }

    ParametrosEntradaSimulacion consultarParametrosEntrada(ConjuntoParametros conjunto) {
        BigDecimal maximo = parametro(conjunto, "consumo_maximo");
        BigDecimal umbral = parametro(conjunto, "umbral_advertencia_consumo");
        if (maximo.signum() <= 0 || umbral.signum() < 0 || umbral.compareTo(maximo) > 0) {
            throw new IllegalStateException("Los límites de consumo no son coherentes.");
        }
        BigDecimal areaMinima = parametro(conjunto, "area_minima");
        BigDecimal areaMaxima = parametro(conjunto, "area_maxima");
        if (areaMinima.signum() <= 0 || areaMinima.compareTo(areaMaxima) > 0) {
            throw new IllegalStateException("Los límites de área no son coherentes.");
        }
        var estratos = conjunto.getEstratos().stream()
                .map(estrato -> new EstratoDisponible(
                        estrato.getNumero(), estrato.getTipoAjuste().name(), estrato.getPorcentajeAjuste()))
                .toList();
        return new ParametrosEntradaSimulacion(maximo, umbral, areaMinima, areaMaxima,
                parametro(conjunto, "consumo_subsistencia"), estratos);
    }

    void validarConsumos(List<BigDecimal> consumos, ParametrosEntradaSimulacion parametros) {
        List<String> errores = new ArrayList<>();
        revisarConsumos(consumos, parametros, errores, new ArrayList<>());
        rechazarSiHayErrores(errores);
    }

    private void revisarConsumos(List<BigDecimal> consumos, ParametrosEntradaSimulacion parametros,
                                 List<String> errores, List<String> advertencias) {
        for (int indice = 0; indice < consumos.size(); indice++) {
            BigDecimal consumo = consumos.get(indice);
            if (consumo.compareTo(parametros.consumoMaximo()) > 0) {
                errores.add("El consumo del mes " + (indice + 1) + " supera el máximo de "
                        + parametros.consumoMaximo().toPlainString() + " kWh.");
            } else if (consumo.compareTo(parametros.umbralAdvertenciaConsumo()) > 0) {
                advertencias.add("Consumo poco común en el mes " + (indice + 1) + ": revise el valor ingresado.");
            }
        }
    }

    private void validarArea(BigDecimal area, ParametrosEntradaSimulacion parametros, List<String> errores) {
        if (area.compareTo(parametros.areaMinima()) < 0 || area.compareTo(parametros.areaMaxima()) > 0) {
            errores.add("El área libre debe estar entre " + parametros.areaMinima().toPlainString()
                    + " y " + parametros.areaMaxima().toPlainString() + " m².");
        }
    }

    private void validarSombra(SolicitudSimulacion solicitud, ConjuntoParametros conjunto, List<String> errores) {
        Set<Long> franjas = conjunto.getFranjas().stream().map(franja -> franja.getId()).collect(Collectors.toSet());
        Set<Long> porciones = conjunto.getPorciones().stream().map(porcion -> porcion.getId()).collect(Collectors.toSet());
        if (franjas.isEmpty() || porciones.isEmpty()) {
            throw new IllegalStateException("Faltan franjas o porciones de sombra en la configuración.");
        }
        Set<Long> respondidas = new HashSet<>();
        for (var respuesta : solicitud.getRespuestasSombra()) {
            if (!franjas.contains(respuesta.getFranjaId())) {
                errores.add("La franja " + respuesta.getFranjaId() + " no existe.");
            }
            if (!respondidas.add(respuesta.getFranjaId())) {
                errores.add("La franja " + respuesta.getFranjaId() + " está respondida más de una vez.");
            }
            if (!porciones.contains(respuesta.getPorcionId())) {
                errores.add("La porción de sombra " + respuesta.getPorcionId() + " no existe.");
            }
        }
        if (!respondidas.containsAll(franjas)) {
            errores.add("Debe responder la sombra de todas las franjas configuradas.");
        }
    }

    private BigDecimal parametro(ConjuntoParametros conjunto, String clave) {
        BigDecimal valor = conjunto.getParametros().get(clave);
        if (valor == null) {
            throw new IllegalStateException("Falta el parámetro de configuración: " + clave + ".");
        }
        return valor;
    }

    private void rechazarSiHayErrores(List<String> errores) {
        if (!errores.isEmpty()) {
            throw new DatosSimulacionInvalidosException(errores);
        }
    }
}
