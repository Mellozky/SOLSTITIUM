package com.solstitium.solstitium.model.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SolicitudActualizacionParametros(
        @NotEmpty(message = "Debe enviar al menos un cambio.") List<@NotNull @Valid CambioParametro> cambios) {
    @JsonAnySetter
    public void rechazarCampoDesconocido(String campo, Object valor) {
        throw new IllegalArgumentException("La solicitud contiene un campo no permitido.");
    }
}
