package com.solstitium.solstitium.model.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import tools.jackson.databind.annotation.JsonDeserialize;

public record SolicitudPanel(
        @NotBlank(message = "La marca es obligatoria.") @Size(max = 60, message = "La marca admite hasta 60 caracteres.")
        @JsonDeserialize(using = DeserializadoresCatalogo.Texto.class) String marca,
        @NotBlank(message = "El modelo es obligatorio.") @Size(max = 60, message = "El modelo admite hasta 60 caracteres.")
        @JsonDeserialize(using = DeserializadoresCatalogo.Texto.class) String modelo,
        @NotNull(message = "La potencia es obligatoria.") @Positive(message = "La potencia debe ser positiva.")
        @Digits(integer = 5, fraction = 2, message = "La potencia admite hasta cinco enteros y dos decimales, sin redondeo.")
        @JsonDeserialize(using = DeserializadoresCatalogo.Numero.class) BigDecimal potenciaWp,
        @NotNull(message = "El área es obligatoria.") @Positive(message = "El área debe ser positiva.")
        @Digits(integer = 2, fraction = 3, message = "El área admite hasta dos enteros y tres decimales, sin redondeo.")
        @JsonDeserialize(using = DeserializadoresCatalogo.Numero.class) BigDecimal areaM2) {
    @JsonAnySetter
    public void rechazarCampoDesconocido(String campo, Object valor) {
        throw new IllegalArgumentException("La solicitud del panel contiene un campo no permitido.");
    }
}
