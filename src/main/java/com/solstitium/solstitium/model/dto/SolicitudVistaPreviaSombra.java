package com.solstitium.solstitium.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SolicitudVistaPreviaSombra(
        @NotEmpty(message = "Debe responder las condiciones de sombra de todas las franjas.")
        List<@NotNull(message = "La respuesta de sombra no puede estar vacía.") @Valid RespuestaSombra> respuestasSombra) {}
