package com.solstitium.solstitium.model.dto;

import java.math.BigDecimal;
import java.util.List;

public record ConsultaPaneles(List<PanelCatalogo> paneles, List<Limite> limites) {
    public record Limite(String campo, BigDecimal valorMinimo, BigDecimal valorMaximo) {}
}
