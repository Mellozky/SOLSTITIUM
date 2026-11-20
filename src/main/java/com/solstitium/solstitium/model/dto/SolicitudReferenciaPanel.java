package com.solstitium.solstitium.model.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

public record SolicitudReferenciaPanel(
        @NotNull(message = "Debe confirmar el cambio de referencia.")
        @AssertTrue(message = "Debe confirmar el cambio de referencia.")
        @JsonDeserialize(using = DeserializadoresCatalogo.Booleano.class) Boolean confirmado) {
    @JsonAnySetter
    public void rechazarCampoDesconocido(String campo, Object valor) {
        throw new IllegalArgumentException("Solo se permite la confirmación del cambio de referencia.");
    }
}
