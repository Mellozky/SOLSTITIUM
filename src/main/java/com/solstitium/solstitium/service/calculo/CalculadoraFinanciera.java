package com.solstitium.solstitium.service.calculo;

import com.solstitium.solstitium.model.dto.ConjuntoParametros;
import com.solstitium.solstitium.model.dto.ResultadoConfiguracion;
import com.solstitium.solstitium.model.dto.SolicitudSimulacion;
import java.math.BigDecimal;
import java.math.MathContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** RF-05: generación aprovechable, ahorro por tramos y cobertura. No consulta la BD. */
@Component
@RequiredArgsConstructor
public class CalculadoraFinanciera {

    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private final CalculadoraSombra calculadoraSombra;
    private final CalculadoraTarifa calculadoraTarifa;

    public void calcularGeneracionYAhorro(ResultadoConfiguracion resultado, SolicitudSimulacion solicitud,
                                          ConjuntoParametros conjunto) {
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
        var sombra = calculadoraSombra.calcularFactor(solicitud.getRespuestasSombra(), conjunto);
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
