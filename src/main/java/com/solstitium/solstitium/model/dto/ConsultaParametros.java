package com.solstitium.solstitium.model.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Proyección pública, sin referencias a entidades JPA. */
public record ConsultaParametros(String seccion, List<Registro> registros, List<Limite> limites) {
    public record Registro(String tabla, String registroId, Map<String, Object> valores) {}
    public record Limite(String tabla, String campo, String clave, BigDecimal valorMinimo, BigDecimal valorMaximo) {}
}
