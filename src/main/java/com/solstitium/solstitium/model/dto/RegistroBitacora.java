package com.solstitium.solstitium.model.dto;

import java.time.LocalDateTime;

public record RegistroBitacora(Long id, String administrador, LocalDateTime fecha, String tabla,
                              String registroId, String campo, String valorAnterior, String valorNuevo) {}
