package com.solstitium.solstitium.service.calculo;

import com.solstitium.solstitium.model.dto.ConjuntoParametros;
import com.solstitium.solstitium.model.dto.FactorSombra;
import com.solstitium.solstitium.model.dto.ResultadoConfiguracion;
import com.solstitium.solstitium.model.dto.SolicitudSimulacion;
import java.math.BigDecimal;
import java.math.MathContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** RF-05/RF-06: generación, ahorro e indicadores financieros. No consulta la BD. */
@Component
@RequiredArgsConstructor
public class CalculadoraFinanciera {

    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private final CalculadoraSombra calculadoraSombra;
    private final CalculadoraTarifa calculadoraTarifa;

    /** Firma del ERS: completa los indicadores de un resultado técnico existente. */
    public void calcularIndicadores(ResultadoConfiguracion resultado, SolicitudSimulacion solicitud,
                                    ConjuntoParametros conjunto) {
        calcularGeneracionYAhorro(resultado, solicitud, conjunto);
        calcularRentabilidad(resultado, conjunto);
    }

    /** Reutiliza un factor ya calculado, incluido el escenario informativo sin sombra. */
    public void calcularIndicadores(ResultadoConfiguracion resultado, SolicitudSimulacion solicitud,
                                    ConjuntoParametros conjunto, FactorSombra sombra) {
        calcularGeneracionYAhorro(resultado, solicitud, conjunto, sombra);
        calcularRentabilidad(resultado, conjunto);
    }

    public void calcularRentabilidad(ResultadoConfiguracion resultado, ConjuntoParametros conjunto) {
        if (resultado == null || resultado.getPaneles() < 0) {
            throw new IllegalArgumentException("Debe proporcionar un resultado técnico válido.");
        }
        if (resultado.getPaneles() == 0) {
            return;
        }
        BigDecimal inversion = resultado.getCostoTotal();
        BigDecimal ahorro = resultado.getAhorroMensual();
        if (inversion == null || inversion.signum() <= 0 || ahorro == null || ahorro.signum() < 0) {
            throw new IllegalArgumentException("Debe calcular una inversión positiva y un ahorro no negativo.");
        }
        BigDecimal vidaConfigurada = parametroPositivo(conjunto, "vida_util");
        BigDecimal incremento = conjunto.getParametros().get("incremento_anual_tarifa");
        int vida;
        try {
            vida = LimitesCalculoFinanciero.validarHorizonte(vidaConfigurada, incremento);
        } catch (IllegalArgumentException excepcion) {
            throw new IllegalStateException(excepcion.getMessage(), excepcion);
        }
        BigDecimal tasa = incremento.movePointLeft(2);
        BigDecimal anual = ahorro.multiply(new BigDecimal("12"));
        BigDecimal total = acumulado(anual, tasa, vida);
        resultado.setRoi(total.subtract(inversion, PRECISION).divide(inversion, PRECISION).movePointRight(2));
        BigDecimal recuperacion = null;
        if (total.compareTo(inversion) >= 0) {
            int inferior = 1;
            int superior = vida;
            // Busca el primer año de recuperación sin recorrer horizontes grandes año por año.
            while (inferior < superior) {
                int medio = inferior + (superior - inferior) / 2;
                if (acumulado(anual, tasa, medio).compareTo(inversion) >= 0) {
                    superior = medio;
                } else {
                    inferior = medio + 1;
                }
            }
            BigDecimal previo = acumulado(anual, tasa, inferior - 1);
            BigDecimal ahorroDelAnio = anual.multiply(BigDecimal.ONE.add(tasa).pow(inferior - 1, PRECISION), PRECISION);
            recuperacion = BigDecimal.valueOf(inferior - 1)
                    .add(inversion.subtract(previo).divide(ahorroDelAnio, PRECISION));
        }
        resultado.setTiempoRecuperacion(recuperacion);
        resultado.setClasificacion(clasificar(recuperacion, conjunto));
    }

    private BigDecimal acumulado(BigDecimal anual, BigDecimal tasa, int anios) {
        if (anios == 0 || anual.signum() == 0) {
            return BigDecimal.ZERO;
        }
        if (tasa.signum() == 0) {
            return anual.multiply(BigDecimal.valueOf(anios));
        }
        return BigDecimal.ONE.add(tasa).pow(anios, PRECISION).subtract(BigDecimal.ONE, PRECISION)
                .divide(tasa, PRECISION).multiply(anual, PRECISION);
    }

    private String clasificar(BigDecimal recuperacion, ConjuntoParametros conjunto) {
        var coincidencias = conjunto.getRangos().stream().filter(rango -> {
            if (recuperacion == null) {
                return rango.getAniosMaximo() == null;
            }
            boolean superaMinimo = recuperacion.compareTo(rango.getAniosMinimo()) > 0
                    || (rango.getAniosMinimo().signum() == 0 && recuperacion.signum() == 0);
            return superaMinimo && (rango.getAniosMaximo() == null
                    || recuperacion.compareTo(rango.getAniosMaximo()) <= 0);
        }).toList();
        if (coincidencias.size() != 1) {
            throw new IllegalStateException("La recuperación debe corresponder a un único rango de viabilidad.");
        }
        return coincidencias.getFirst().getClasificacion();
    }

    public void calcularGeneracionYAhorro(ResultadoConfiguracion resultado, SolicitudSimulacion solicitud,
                                          ConjuntoParametros conjunto) {
        calcularGeneracionYAhorro(resultado, solicitud, conjunto, null);
    }

    private void calcularGeneracionYAhorro(ResultadoConfiguracion resultado, SolicitudSimulacion solicitud,
                                           ConjuntoParametros conjunto, FactorSombra sombraPrecalculada) {
        if (resultado == null || resultado.getPaneles() < 0) {
            throw new IllegalArgumentException("Debe proporcionar un resultado técnico válido.");
        }
        // Sin espacio no se calculan indicadores, ni siquiera como ceros ficticios.
        if (resultado.getPaneles() == 0) {
            return;
        }
        if (resultado.getPotenciaKwp() == null || resultado.getPotenciaKwp().signum() <= 0) {
            throw new IllegalArgumentException("La potencia instalada debe ser positiva.");
        }
        var configuracion = conjunto.getConfiguraciones().stream()
                .filter(actual -> actual.getCodigo().equals(resultado.getConfiguracion())).findFirst()
                .orElseThrow(() -> new IllegalStateException("La configuración del resultado no existe."));
        BigDecimal recuperacion = configuracion.getCoeficienteRecuperacion();
        if (recuperacion == null || recuperacion.signum() < 0 || recuperacion.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException("El coeficiente de recuperación debe estar entre cero y uno.");
        }
        if (configuracion.getCodigo().equals("central") && recuperacion.signum() != 0) {
            throw new IllegalStateException("La configuración central debe conservar coeficiente de recuperación cero.");
        }
        var sombra = sombraPrecalculada != null ? sombraPrecalculada
                : calculadoraSombra.calcularFactor(solicitud.getRespuestasSombra(), conjunto);
        BigDecimal factor = BigDecimal.ONE.subtract(sombra.getPerdida().multiply(BigDecimal.ONE.subtract(recuperacion)));
        BigDecimal hsp = parametroPositivo(conjunto, "hsp");
        BigDecimal rendimiento = parametroPositivo(conjunto, "factor_rendimiento");
        if (rendimiento.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException("El rendimiento no puede superar uno.");
        }
        BigDecimal consumo = solicitud.consumoPromedio();
        BigDecimal generacion = resultado.getPotenciaKwp().multiply(hsp).multiply(rendimiento)
                .multiply(factor).multiply(new BigDecimal("30"));
        BigDecimal aprovechada = generacion.min(consumo);
        // Diferencia de facturas: se evita primero el tramo pleno y luego el subsidiado.
        BigDecimal gastoSinSolar = calculadoraTarifa.calcularCostoMensual(consumo, solicitud.getEstrato(), conjunto);
        BigDecimal gastoConSolar = calculadoraTarifa.calcularCostoMensual(consumo.subtract(aprovechada), solicitud.getEstrato(), conjunto);
        resultado.setEnergiaMensual(aprovechada);
        resultado.setAhorroMensual(gastoSinSolar.subtract(gastoConSolar));
        resultado.setGastoMensualConSistema(gastoConSolar);
        resultado.setCobertura(aprovechada.divide(consumo, PRECISION).movePointRight(2));
    }

    private BigDecimal parametroPositivo(ConjuntoParametros conjunto, String clave) {
        BigDecimal valor = conjunto.getParametros().get(clave);
        if (valor == null || valor.signum() <= 0) {
            throw new IllegalStateException("El parámetro " + clave + " debe existir y ser positivo.");
        }
        return valor;
    }
}
