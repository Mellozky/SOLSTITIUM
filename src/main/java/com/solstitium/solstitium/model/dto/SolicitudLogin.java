package com.solstitium.solstitium.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SolicitudLogin(
        @NotBlank @Size(max = 50) String usuario,
        @NotBlank @Size(max = 72) String contrasena) {
}
