package com.solstitium.solstitium.model.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

/** Un único destino de edición; nunca contiene la identidad del administrador. */
public record CambioParametro(
        @NotBlank @Size(max = 50) String tabla,
        @NotBlank @Size(max = 50) String campo,
        @NotBlank @Size(max = 50) String registroId,
        @JsonDeserialize(using = DeserializadorValorParametro.class) Object valorNuevo) {
    @JsonAnySetter
    public void rechazarCampoDesconocido(String campo, Object valor) {
        throw new IllegalArgumentException("El cambio contiene un campo no permitido.");
    }
}
