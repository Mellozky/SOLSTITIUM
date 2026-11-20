package com.solstitium.solstitium.model.dto;

import java.util.List;

/** Contrato de error sin trazas, consultas SQL ni credenciales. */
public record RespuestaErrorSimulacion(String mensaje, List<String> errores) {
}
