package com.solstitium.solstitium.model.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

public record ParametrosEntradaSimulacion(
        BigDecimal consumoMaximo,
        BigDecimal umbralAdvertenciaConsumo,
        BigDecimal areaMinima,
        BigDecimal areaMaxima,
        BigDecimal consumoSubsistencia,
        List<EstratoDisponible> estratos,
        List<FranjaDisponible> franjas,
        List<PorcionDisponible> porciones) {

    public record EstratoDisponible(int numero, String tipoAjuste, BigDecimal porcentajeAjuste) {}

    public record FranjaDisponible(Long id, String nombre, LocalTime horaInicio, LocalTime horaFin) {}

    public record PorcionDisponible(Long id, String etiqueta) {}
}
