package com.solstitium.solstitium.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Opción elegida para una franja; el residente no introduce un factor técnico. */
@Getter
@Setter
@NoArgsConstructor
public class RespuestaSombra {

    @NotNull(message = "Debe indicar la franja horaria.")
    @Positive(message = "El identificador de la franja debe ser positivo.")
    private Long franjaId;

    @NotNull(message = "Debe seleccionar la porción afectada por sombra.")
    @Positive(message = "El identificador de la porción debe ser positivo.")
    private Long porcionId;
}
