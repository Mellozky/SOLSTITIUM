package com.solstitium.solstitium.model.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

public record SolicitudEstadoPanel(
        @NotNull(message = "Debe indicar el estado activo del panel.")
        @JsonDeserialize(using = DeserializadoresCatalogo.Booleano.class) Boolean activo) {
    @JsonAnySetter
    public void rechazarCampoDesconocido(String campo, Object valor) {
        throw new IllegalArgumentException("Solo se permite el estado activo.");
    }
}
