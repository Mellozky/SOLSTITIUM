package com.solstitium.solstitium.service;

import com.solstitium.solstitium.exception.DatosSimulacionInvalidosException;
import com.solstitium.solstitium.model.dto.ConjuntoParametros;
import com.solstitium.solstitium.model.dto.SolicitudSimulacion;
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
        validarConsumosYArea(solicitud, conjunto, errores, advertencias);
        if (conjunto.getEstratos().stream().noneMatch(estrato -> estrato.getNumero() == solicitud.getEstrato())) {
            errores.add("El estrato seleccionado no está disponible en la configuración.");
        }
        validarSombra(solicitud, conjunto, errores);
        rechazarSiHayErrores(errores);
        return List.copyOf(advertencias);
    }

    private void validarConsumosYArea(SolicitudSimulacion solicitud, ConjuntoParametros conjunto,
                                     List<String> errores, List<String> advertencias) {
        BigDecimal maximo = parametro(conjunto, "consumo_maximo");
        BigDecimal umbral = parametro(conjunto, "umbral_advertencia_consumo");
        BigDecimal areaMinima = parametro(conjunto, "area_minima");
        BigDecimal areaMaxima = parametro(conjunto, "area_maxima");
        if (maximo.signum() <= 0 || umbral.signum() < 0 || umbral.compareTo(maximo) > 0
                || areaMinima.signum() <= 0 || areaMinima.compareTo(areaMaxima) > 0) {
            throw new IllegalStateException("Los límites de consumo o área no son coherentes.");
        }
        for (int indice = 0; indice < solicitud.getConsumos().size(); indice++) {
            BigDecimal consumo = solicitud.getConsumos().get(indice);
            if (consumo.compareTo(maximo) > 0) {
                errores.add("El consumo del mes " + (indice + 1) + " supera el máximo de " + maximo.toPlainString() + " kWh.");
            } else if (consumo.compareTo(umbral) > 0) {
                advertencias.add("Consumo poco común en el mes " + (indice + 1) + ": revise el valor ingresado.");
            }
        }
        if (solicitud.getAreaLibre().compareTo(areaMinima) < 0 || solicitud.getAreaLibre().compareTo(areaMaxima) > 0) {
            errores.add("El área libre debe estar entre " + areaMinima.toPlainString()
                    + " y " + areaMaxima.toPlainString() + " m².");
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
