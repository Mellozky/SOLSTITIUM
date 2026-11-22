package com.solstitium.solstitium.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record SolicitudPromedioConsumo(
        @NotNull(message = "Debe ingresar los consumos mensuales.")
        @Size(min = 1, max = 12, message = "Debe ingresar entre 1 y 12 meses de consumo.")
        List<@NotNull(message = "El consumo mensual es obligatorio.")
                @Positive(message = "El consumo mensual debe ser mayor que cero.") BigDecimal> consumos) {}
