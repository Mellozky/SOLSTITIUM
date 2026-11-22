package com.solstitium.solstitium.model.dto;

import java.math.BigDecimal;
import java.util.List;

public record ParametrosEntradaSimulacion(
        BigDecimal consumoMaximo,
        BigDecimal umbralAdvertenciaConsumo,
        BigDecimal areaMinima,
        BigDecimal areaMaxima,
        BigDecimal consumoSubsistencia,
        List<EstratoDisponible> estratos) {

    public record EstratoDisponible(int numero, String tipoAjuste, BigDecimal porcentajeAjuste) {}
}
