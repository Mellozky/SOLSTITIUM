package com.solstitium.solstitium.service.calculo;

import com.solstitium.solstitium.model.dto.ConjuntoParametros;
import com.solstitium.solstitium.model.dto.FactorSombra;
import com.solstitium.solstitium.model.dto.ResultadoConfiguracion;
import com.solstitium.solstitium.model.dto.SolicitudSimulacion;
import com.solstitium.solstitium.model.entity.Configuracion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/** RF-04: dimensionamiento y costo instalado de una configuración (ERS, figura 4). */
@Component
public class CalculadoraTecnica {

    // Convenciones del modelo RF-04, no parámetros de negocio configurables.
    private static final BigDecimal DIAS_MES = new BigDecimal("30");
    private static final BigDecimal PASO_INVERSOR_KW = new BigDecimal("0.5");

    public ResultadoConfiguracion dimensionar(SolicitudSimulacion solicitud, FactorSombra sombra,
                                              Configuracion configuracion, ConjuntoParametros conjunto) {
        validarEntradas(solicitud, sombra, configuracion, conjunto);
        var panel = conjunto.getPanelReferencia();
        BigDecimal hsp = positivo(parametro(conjunto, "hsp"), "HSP");
        BigDecimal rendimiento = fraccionPositiva(parametro(conjunto, "factor_rendimiento"), "rendimiento");
        BigDecimal aprovechamiento = fraccionPositiva(parametro(conjunto, "factor_aprovechamiento"), "aprovechamiento");
        BigDecimal relacion = positivo(parametro(conjunto, "relacion_cc_ca"), "relación CC/CA");
        BigDecimal tope = parametro(conjunto, "tope_sobredimensionamiento").movePointLeft(2);
        if (tope.signum() < 0) {
            throw new IllegalStateException("El tope de sobredimensionamiento no puede ser negativo.");
        }
        BigDecimal potenciaPanel = positivo(panel.getPotenciaWp(), "potencia del panel").movePointLeft(3);
        BigDecimal areaPanel = positivo(panel.getAreaM2(), "área del panel");
        BigDecimal costoKwp = positivo(configuracion.getCostoKwp(), "costo por kWp");

        BigDecimal sumaConsumos = solicitud.getConsumos().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        // Equivale a usar el promedio, pero evita redondear divisiones periódicas antes del CEILING.
        BigDecimal denominador = BigDecimal.valueOf(solicitud.getConsumos().size())
                .multiply(DIAS_MES).multiply(hsp).multiply(rendimiento).multiply(potenciaPanel);
        BigDecimal multiplicadorMaximo = BigDecimal.ONE.add(tope);
        BigDecimal numerador;
        if (sombra.getFactor().signum() == 0
                || sombra.getFactor().multiply(multiplicadorMaximo).compareTo(BigDecimal.ONE) <= 0) {
            numerador = sumaConsumos.multiply(multiplicadorMaximo);
        } else {
            // 1 + margen = 1 / factor, cuando aún no se alcanza el tope.
            numerador = sumaConsumos;
            denominador = denominador.multiply(sombra.getFactor());
        }
        BigDecimal necesarios = numerador.divide(denominador, 0, RoundingMode.CEILING);
        BigDecimal caben = solicitud.getAreaLibre().multiply(aprovechamiento)
                .divide(areaPanel, 0, RoundingMode.FLOOR);
        int paneles;
        try {
            paneles = necesarios.min(caben).intValueExact();
        } catch (ArithmeticException excepcion) {
            throw new IllegalStateException("El número de paneles supera la capacidad de representación.", excepcion);
        }

        BigDecimal potencia = potenciaPanel.multiply(BigDecimal.valueOf(paneles));
        BigDecimal inversor = potencia.divide(relacion.multiply(PASO_INVERSOR_KW), 0, RoundingMode.CEILING)
                .multiply(PASO_INVERSOR_KW);
        ResultadoConfiguracion resultado = new ResultadoConfiguracion();
        resultado.setConfiguracion(configuracion.getCodigo());
        resultado.setPaneles(paneles);
        resultado.setPotenciaKwp(potencia);
        resultado.setInversorKw(inversor);
        resultado.setCostoTotal(potencia.multiply(costoKwp));
        return resultado;
    }

    private void validarEntradas(SolicitudSimulacion solicitud, FactorSombra sombra,
                                Configuracion configuracion, ConjuntoParametros conjunto) {
        if (solicitud == null || solicitud.getConsumos() == null || solicitud.getConsumos().isEmpty()
                || solicitud.getConsumos().stream().anyMatch(consumo -> consumo == null || consumo.signum() <= 0)
                || solicitud.getAreaLibre() == null || solicitud.getAreaLibre().signum() < 0) {
            throw new IllegalArgumentException("Debe proporcionar consumos positivos y un área libre no negativa.");
        }
        if (sombra == null || sombra.getFactor() == null || sombra.getFactor().signum() < 0
                || sombra.getFactor().compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("El factor de sombra debe estar entre cero y uno.");
        }
        if (conjunto == null || conjunto.getPanelReferencia() == null
                || !conjunto.getPanelReferencia().isActivo() || !conjunto.getPanelReferencia().isEsReferencia()) {
            throw new IllegalStateException("Debe existir un panel de referencia activo.");
        }
        if (configuracion == null || configuracion.getCodigo() == null || configuracion.getCodigo().isBlank()) {
            throw new IllegalArgumentException("Debe indicar una configuración para dimensionar.");
        }
    }

    private BigDecimal parametro(ConjuntoParametros conjunto, String clave) {
        BigDecimal valor = conjunto.getParametros().get(clave);
        if (valor == null) {
            throw new IllegalStateException("Falta el parámetro técnico: " + clave + ".");
        }
        return valor;
    }

    private BigDecimal positivo(BigDecimal valor, String nombre) {
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalStateException("El valor de " + nombre + " debe ser positivo.");
        }
        return valor;
    }

    private BigDecimal fraccionPositiva(BigDecimal valor, String nombre) {
        positivo(valor, nombre);
        if (valor.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException("El valor de " + nombre + " no puede superar uno.");
        }
        return valor;
    }
}
