package com.solstitium.solstitium.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Datos transitorios del residente (ERS, figura 5); no se almacenan en tablas. */
@Getter
@Setter
@NoArgsConstructor
public class SolicitudSimulacion {

    @NotNull(message = "Debe ingresar los consumos mensuales.")
    @Size(min = 1, max = 12, message = "Debe ingresar entre 1 y 12 meses de consumo.")
    private List<@NotNull(message = "El consumo mensual es obligatorio.")
            @Positive(message = "El consumo mensual debe ser mayor que cero.") BigDecimal> consumos;

    @Positive(message = "La factura, si se informa, debe ser mayor que cero.")
    private BigDecimal valorFactura;

    // Integer permite distinguir un estrato ausente de un valor numérico inválido.
    @NotNull(message = "Debe seleccionar el estrato.")
    @Min(value = 1, message = "El estrato debe estar entre 1 y 6.")
    @Max(value = 6, message = "El estrato debe estar entre 1 y 6.")
    private Integer estrato;

    @NotNull(message = "Debe indicar el área libre del techo.")
    @Positive(message = "El área libre debe ser mayor que cero.")
    private BigDecimal areaLibre;

    @NotEmpty(message = "Debe responder las condiciones de sombra de todas las franjas.")
    @Valid
    private List<@NotNull(message = "La respuesta de sombra no puede estar vacía.") RespuestaSombra> respuestasSombra;

    /** Promedio en kWh; validar la solicitud antes de usarlo. No redondea a pesos ni a paneles. */
    public BigDecimal consumoPromedio() {
        if (consumos == null || consumos.isEmpty()
                || consumos.stream().anyMatch(consumo -> consumo == null || consumo.signum() <= 0)) {
            throw new IllegalStateException("Debe validar los consumos antes de calcular su promedio.");
        }
        BigDecimal suma = consumos.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return suma.divide(BigDecimal.valueOf(consumos.size()), MathContext.DECIMAL128);
    }
}
