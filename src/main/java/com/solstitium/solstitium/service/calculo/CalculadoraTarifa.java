package com.solstitium.solstitium.service.calculo;

import com.solstitium.solstitium.model.dto.ConjuntoParametros;
import com.solstitium.solstitium.model.dto.SolicitudSimulacion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Cálculo reutilizable de RF-02 y comparación de factura de RF-01, sin acceso directo a BD. */
@Component
public class CalculadoraTarifa {

    /**
     * Valora un consumo en COP, sin redondeos intermedios. Admite cero para futuros
     * cálculos internos de consumo restante; el formulario exige consumos positivos.
     */
    public BigDecimal calcularCostoMensual(BigDecimal consumo, int numeroEstrato, ConjuntoParametros conjunto) {
        if (consumo == null || consumo.signum() < 0) {
            throw new IllegalArgumentException("El consumo para calcular la tarifa no puede ser nulo ni negativo.");
        }
        BigDecimal costoUnitario = parametro(conjunto, "costo_unitario_kwh");
        BigDecimal subsistencia = parametro(conjunto, "consumo_subsistencia");
        if (costoUnitario.signum() <= 0 || subsistencia.signum() < 0) {
            throw new IllegalStateException("El costo unitario debe ser positivo y la subsistencia no puede ser negativa.");
        }
        var estrato = conjunto.getEstratos().stream()
                .filter(actual -> actual.getNumero() == numeroEstrato).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("El estrato seleccionado no está configurado."));
        BigDecimal porcentaje = estrato.getPorcentajeAjuste();
        if (estrato.getTipoAjuste() == null || porcentaje == null
                || porcentaje.signum() < 0 || porcentaje.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalStateException("El ajuste tarifario del estrato no es válido.");
        }
        BigDecimal ajuste = porcentaje.movePointLeft(2);
        return switch (estrato.getTipoAjuste()) {
            case SUBSIDIO -> {
                BigDecimal subsidiado = consumo.min(subsistencia);
                BigDecimal excedente = consumo.subtract(subsidiado);
                yield subsidiado.multiply(costoUnitario).multiply(BigDecimal.ONE.subtract(ajuste))
                        .add(excedente.multiply(costoUnitario));
            }
            case NINGUNO -> consumo.multiply(costoUnitario);
            case CONTRIBUCION -> consumo.multiply(costoUnitario).multiply(BigDecimal.ONE.add(ajuste));
        };
    }

    /** La factura se compara con el mes más reciente, no con el consumo promedio. */
    public Optional<String> obtenerAdvertenciaFactura(SolicitudSimulacion solicitud, ConjuntoParametros conjunto) {
        if (solicitud == null) {
            throw new IllegalArgumentException("Debe enviar los datos de la simulación.");
        }
        if (solicitud.getValorFactura() == null) {
            return Optional.empty();
        }
        if (solicitud.getValorFactura().signum() <= 0 || solicitud.getEstrato() == null
                || solicitud.getConsumos() == null || solicitud.getConsumos().isEmpty()) {
            throw new IllegalArgumentException("Debe validar el consumo, el estrato y la factura antes de compararlos.");
        }
        BigDecimal estimado = calcularCostoMensual(solicitud.getConsumos().getLast(), solicitud.getEstrato(), conjunto);
        BigDecimal umbral = parametro(conjunto, "umbral_diferencia_factura");
        if (umbral.signum() < 0) {
            throw new IllegalStateException("El umbral de diferencia de factura no puede ser negativo.");
        }
        BigDecimal diferencia = solicitud.getValorFactura().subtract(estimado).abs();
        // Evita dividir entre el estimado y mantiene exacto el borde del umbral (CV-13/CV-14).
        if (diferencia.compareTo(estimado.multiply(umbral.movePointLeft(2))) <= 0) {
            return Optional.empty();
        }
        return Optional.of("La factura no coincide con el valor estimado de "
                + estimado.setScale(2, RoundingMode.HALF_UP).toPlainString()
                + " COP para el último mes. Revise el consumo y el estrato; la factura puede incluir otros cobros.");
    }

    private BigDecimal parametro(ConjuntoParametros conjunto, String clave) {
        if (conjunto == null || conjunto.getParametros().get(clave) == null) {
            throw new IllegalStateException("Falta el parámetro de configuración: " + clave + ".");
        }
        return conjunto.getParametros().get(clave);
    }
}
